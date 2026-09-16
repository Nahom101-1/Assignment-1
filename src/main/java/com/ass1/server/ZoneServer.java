package com.ass1.server;

import com.ass1.cache.ResultCache;
import com.ass1.common.QueryResult;
import com.ass1.data.NaiveStatistics;
import com.ass1.proxy.ProxyInterface;
import com.ass1.util.Args;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.LongSupplier;

/**
 * A server of one geographical zone.
 *
 * <p>Threads inside this class (as required by the assignment):</p>
 * <ul>
 *   <li><b>many RMI threads</b> accept incoming calls, sleep the simulated network
 *       delay and put a task into the waiting list;</li>
 *   <li><b>exactly one worker thread</b> takes tasks from the waiting list in FIFO
 *       order and computes the answers.</li>
 * </ul>
 *
 * <p>Because the calling RMI thread waits for the worker to finish, the client
 * still sees a normal blocking remote call.</p>
 */
public class ZoneServer implements ServerInterface {

    /** Base network delay for a call inside the same zone. */
    public static final long BASE_DELAY_MS = 80;
    /** Extra delay per zone of distance. */
    public static final long DELAY_PER_ZONE_MS = 30;
    /** Cache size demanded by the assignment for the server side cache. */
    public static final int CACHE_CAPACITY = 150;

    private final NaiveStatistics statistics;
    private final ResultCache cache;
    private final BlockingQueue<Task> waitingList = new LinkedBlockingQueue<>();
    /** "unix timestamp;queue length" samples, written to disk when the server stops. */
    private final Queue<String> queueSamples = new ConcurrentLinkedQueue<>();
    /** The one and only execution thread. */
    private final Thread worker = new Thread(this::workerLoop, "zone-server-worker");
    /** Registries must stay referenced, otherwise RMI may garbage collect them. */
    private static final List<Registry> REGISTRIES = new CopyOnWriteArrayList<>();

    private volatile int zone = 0;
    private volatile boolean running = true;

    public ZoneServer(NaiveStatistics statistics, ResultCache cache) {
        this.statistics = statistics;
        this.cache = cache;
    }

    // ---------------------------------------------------------------- remote API

    @Override
    public QueryResult getPopulationofCountry(String country, int clientZone) throws RemoteException {
        return submit("getPopulationofCountry|" + country,
                () -> statistics.populationOfCountry(country), clientZone);
    }

    @Override
    public QueryResult getNumberofCities(String country, long threshold, String comparison, int clientZone)
            throws RemoteException {
        return submit("getNumberofCities|" + country + "|" + threshold + "|" + comparison,
                () -> statistics.numberOfCities(country, threshold, comparison), clientZone);
    }

    @Override
    public QueryResult getNumberofCountries(int cityCount, long threshold, String comparison, int clientZone)
            throws RemoteException {
        return submit("getNumberofCountries|" + cityCount + "|" + threshold + "|" + comparison,
                () -> statistics.numberOfCountries(cityCount, threshold, comparison), clientZone);
    }

    @Override
    public QueryResult getNumberofCountriesMM(int cityCount, long minPopulation, long maxPopulation, int clientZone)
            throws RemoteException {
        return submit("getNumberofCountriesMM|" + cityCount + "|" + minPopulation + "|" + maxPopulation,
                () -> statistics.numberOfCountriesMM(cityCount, minPopulation, maxPopulation), clientZone);
    }

    @Override
    public int getQueueLength() {
        return waitingList.size();
    }

    @Override
    public int getZone() {
        return zone;
    }

    // ------------------------------------------------------------- inner working

    /**
     * Common path of all four remote methods: simulate the network, queue the task,
     * wait for the worker thread and return its answer.
     */
    private QueryResult submit(String cacheKey, LongSupplier computation, int clientZone) throws RemoteException {
        awaitZoneNumber();
        simulateNetwork(clientZone);

        Task task = new Task(cacheKey, computation);
        waitingList.add(task);
        logQueueLength();

        try {
            return task.result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Server " + zone + " was interrupted", e);
        } catch (ExecutionException e) {
            throw new RemoteException("Server " + zone + " failed to process the request", e.getCause());
        }
    }

    /**
     * The server object is reachable a moment before the proxy has given it a zone
     * number. Requests arriving in that moment wait here, otherwise the simulated
     * network delay would be computed with zone 0.
     */
    private void awaitZoneNumber() throws RemoteException {
        for (int attempt = 0; zone == 0 && attempt < 200; attempt++) {
            sleep(50);
        }
        if (zone == 0) {
            throw new RemoteException("Server is not registered at the proxy yet");
        }
    }

    /** Sleeps 80 ms for a local call, 80 + 30 * distance ms for a call from another zone. */
    private void simulateNetwork(int clientZone) {
        long distance = Math.abs(clientZone - zone);
        sleep(BASE_DELAY_MS + distance * DELAY_PER_ZONE_MS);
    }

    /** The single execution thread: FIFO, one request at a time. */
    private void workerLoop() {
        while (running) {
            Task task;
            try {
                task = waitingList.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            logQueueLength();

            long startNanos = System.nanoTime();
            long waitingMs = (startNanos - task.queuedNanos) / 1_000_000;
            try {
                Long cached = cache.get(task.cacheKey);
                long value;
                boolean cacheHit = cached != null;
                if (cacheHit) {
                    value = cached;
                } else {
                    value = task.computation.getAsLong();
                    cache.put(task.cacheKey, value);
                }
                long executionMs = (System.nanoTime() - startNanos) / 1_000_000;
                task.result.complete(new QueryResult(value, executionMs, waitingMs, zone, cacheHit));
            } catch (Throwable error) {
                // never let the worker thread die: report the problem to this one
                // client and continue with the next request
                task.result.completeExceptionally(error);
            }
        }
        // the worker stops: wake up everybody who is still waiting for an answer
        Task pending;
        while ((pending = waitingList.poll()) != null) {
            pending.result.completeExceptionally(new IllegalStateException("Server " + zone + " stopped"));
        }
    }

    /** Stops the worker thread (used by tests and by an orderly shutdown). */
    public void stop() {
        running = false;
        worker.interrupt();
    }

    /**
     * Remembers "unix-timestamp;queue length" in memory. Writing to disk here would
     * slow the server down and falsify the measurements, so the samples are flushed
     * once at shutdown by {@link #writeQueueLog()}.
     */
    private void logQueueLength() {
        queueSamples.add(System.currentTimeMillis() + ";" + waitingList.size());
    }

    /** Writes the collected queue samples to {@code output/server_<zone>_queue.log}. */
    public void writeQueueLog() {
        try {
            Path directory = Files.createDirectories(Path.of("output"));
            Path file = directory.resolve("server_" + zone + "_queue.log");
            StringBuilder content = new StringBuilder("timestamp;queue_length").append(System.lineSeparator());
            for (String sample : queueSamples) {
                content.append(sample).append(System.lineSeparator());
            }
            Files.writeString(file, content.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** One request waiting in / being processed by the server. */
    private static final class Task {
        private final String cacheKey;
        private final LongSupplier computation;
        private final CompletableFuture<QueryResult> result = new CompletableFuture<>();
        private final long queuedNanos = System.nanoTime();

        private Task(String cacheKey, LongSupplier computation) {
            this.cacheKey = cacheKey;
            this.computation = computation;
        }
    }

    // ------------------------------------------------------------------ start-up

    /** Starts one zone server from the command line ({@code java -jar solution.jar server ...}). */
    public static void start(Args options) throws Exception {
        String host = options.get("host", "localhost");
        int port = options.getInt("port", 0);
        String proxyHost = options.get("proxy-host", "localhost");
        int proxyPort = options.getInt("proxy-port", 1099);
        Path dataset = Path.of(options.get("dataset", "data/exercise_1_dataset.csv"));
        ResultCache.Policy policy = ResultCache.Policy.from(options.get("cache", "none"));

        // Zones are handed out in the order the servers register. In Docker all
        // containers start at the same time, so a small delay per server keeps the
        // zone numbers predictable (zone1 -> 1, zone2 -> 2, ...).
        long startDelay = options.getInt("start-delay", 0);
        if (startDelay > 0) {
            sleep(startDelay);
        }

        launch(host, port, proxyHost, proxyPort, dataset, policy);
    }

    /**
     * Creates the server object, publishes it in its own RMI registry and registers
     * it at the proxy. Used by both {@link #start(Args)} and {@link ServerSimulator}.
     *
     * @param port registry port, use 0 to pick a free one automatically
     * @return the started server
     */
    public static ZoneServer launch(String host, int port, String proxyHost, int proxyPort,
                                    Path dataset, ResultCache.Policy policy) throws Exception {
        // RMI sends this host name to the clients; without it Java may send an
        // unreachable address (important inside Docker).
        System.setProperty("java.rmi.server.hostname", host);

        int registryPort = port > 0 ? port : freePort();

        ZoneServer server = new ZoneServer(new NaiveStatistics(dataset),
                new ResultCache(policy, CACHE_CAPACITY));

        // the worker starts first, so a request can never arrive at a server
        // whose waiting list nobody is reading
        server.worker.start();

        ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(server, 0);
        Registry registry = LocateRegistry.createRegistry(registryPort);
        REGISTRIES.add(registry);
        String bindName = "ZoneServer";
        registry.rebind(bindName, stub);

        try {
            // ask the proxy for a zone number
            Registry proxyRegistry = LocateRegistry.getRegistry(proxyHost, proxyPort);
            ProxyInterface proxy = (ProxyInterface) proxyRegistry.lookup(ProxyInterface.BIND_NAME);
            server.zone = proxy.registerServer(host, registryPort, bindName);
        } catch (Exception e) {
            // do not leave a half-started server behind: free the RMI resources
            server.stop();
            UnicastRemoteObject.unexportObject(server, true);
            throw new IllegalStateException("Could not register at the proxy "
                    + proxyHost + ":" + proxyPort + " – is the proxy running?", e);
        }
        server.worker.setName("server-" + server.zone + "-worker");

        // make sure the queue log ends up on disk when the process is stopped
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                server.writeQueueLog();
                System.out.println("Zone server " + server.zone + " stopped, " + server.cache);
            } catch (RuntimeException e) {
                System.err.println("Could not write the queue log: " + e.getMessage());
            }
        }));

        System.out.printf("Zone server %d ready on %s:%d (%s, dataset=%s)%n",
                server.zone, host, registryPort, server.cache, dataset.toAbsolutePath());
        return server;
    }

    /** Asks the operating system for a currently unused TCP port. */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}












