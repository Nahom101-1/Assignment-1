package com.ass1.server;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;
import com.ass1.common.ServerInfo;
import com.ass1.proxy.ProxyInterface;
import com.ass1.server.common.Cache;
import com.ass1.server.common.Task;
import com.ass1.server.common.Worker;
import com.ass1.server.common.Processor;
import com.ass1.util.Args;

import java.nio.file.Path;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.LongSupplier;

import static java.lang.Thread.sleep;

/**
 * A zone server that handles requests from clients and registers itself with the proxy. Each server has a zone number assigned by the proxy, which is used to simulate network latency for requests from clients in different zones.
 * The server maintains a queue of requests to be processed by a worker thread, which handles the actual computation of the requests. The server also maintains a cache of results to improve performance for repeated requests.
 * The server can be started and registered with the proxy using the InitializeServer method, and can be shut down using the shutdown method, which stops the worker thread and withdraws the server from RMI. The server can also report its current workload, which is the number of requests currently waiting in the queue.
 */
public class Server implements ServerInterface {
    public static final String BIND_NAME = "ZoneServer";

    private volatile int zone = 0;
    public static final long BASE_DELAY_MS = 80;

    /** Extra delay per zone of distance for a call from another zone. */
    public static final long DELAY_PER_ZONE_MS = 30;
    public static final int CACHE_CAPACITY = 150;

    /** Queue of requests to be processed by the worker thread. */
    private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

    /*** Processor that handles the actual computation of the requests. */
    private final Processor processor;

    /*** Worker thread that processes requests from the queue. */
    private final Thread requestWorker;

    /**
     * The zone's registry. Held as a field on purpose: {@link LocateRegistry#createRegistry}
     * returns the only strong reference to the registry it creates, and an unreferenced
     * registry can be garbage collected, silently taking the binding down with it.
     */
    private Registry registry;

    public Server(Processor processor) {
        this.processor = processor;
        Cache<String, Long> cache = new Cache<>(CACHE_CAPACITY);
        this.requestWorker = new Thread(new Worker(cache, queue), "requestWorker");
        this.requestWorker.start();
    }

    @Override
    public QueryResult getPopulationOfCountry(String countryName, int clientZone) throws RemoteException {
        return stageRequest("getPopulationOfCountry:" + countryName,
                () -> processor.getPopulationOfCountry(countryName),
                clientZone);
    }

    @Override
    public QueryResult getNumberOfCities(String countryName, int threshold, Comparison comp, int clientZone) throws RemoteException {
        return stageRequest("getNumberOfCities:" + countryName + ":" + threshold + ":" + comp,
                () -> processor.getNumberOfCities(countryName, threshold, comp.name()),
                clientZone);
    }

    @Override
    public QueryResult getNumberOfCountries(int cityCount, int threshold, Comparison comp, int clientZone) throws RemoteException {
        return stageRequest("getNumberOfCountries:" + cityCount + ":" + threshold + ":" + comp,
                () -> processor.getNumberOfCountries(cityCount, threshold, comp.name()),
                clientZone);
    }

    @Override
    public QueryResult getNumberOfCountriesMM(int cityCount, int minPopulation, int maxPopulation, int clientZone) throws RemoteException {
        return stageRequest("getNumberOfCountriesMM:" + cityCount + ":" + minPopulation + ":" + maxPopulation,
                () -> processor.getNumberOfCountriesMM(cityCount, minPopulation, maxPopulation),
                clientZone);
    }

    /** Number of requests currently waiting in this server's waiting list. */
    @Override
    public int getCurrentWorkload() throws RemoteException {
        return queue.size();
    }

    /** Queues the request and blocks until the worker thread is done with it. */
    private QueryResult stageRequest(String cacheKey, LongSupplier computation, int clientZone) throws RemoteException {
        simulateNetworkLatency(clientZone);

        Task task = new Task(cacheKey, computation, clientZone);
        queue.add(task);
        try {
            return task.result.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RemoteException("Interrupted while waiting for " + cacheKey, e);
        } catch (ExecutionException e) {
            throw new RemoteException("Failed to process " + cacheKey, e);
        }
    }

    /** Sleeps 80 ms for a call from the same zone. 80 + 30 * distance for a call from another zone. */
    private void simulateNetworkLatency(int clientZone) {
        if(clientZone == zone) {
            try {
                sleep(BASE_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        else{
            long distance = Math.abs(clientZone - zone);
            try {
                sleep(BASE_DELAY_MS + DELAY_PER_ZONE_MS * distance);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Stops the worker thread and withdraws the server from RMI. Safe to call twice. */
    public void shutdown() {
        requestWorker.interrupt();

        if (registry != null) {
            try {
                registry.unbind(BIND_NAME);
            } catch (Exception ignored) {
                // Already gone, or the registry died with the process that owns it.
            }
        }
        try {
            UnicastRemoteObject.unexportObject(this, true);
        } catch (NoSuchObjectException e) {
            // Never exported, or already unexported: nothing to undo.
        }
    }

    /**
     * Starts one zone server and registers it with the proxy.
     *
     */
    public static void StartServer(Args options) throws Exception {

        String serverHost = options.get("server-host", "localhost");
        int serverPort = options.getInt("server-port", 1101);

        String proxyHost = options.get("proxy-host", "localhost");
        int proxyPort = options.getInt("proxy-port", 1099);

        Path dataset = Path.of(options.get("dataset", "data/exercise_1_dataset.csv"));

        // Baked into every stub this JVM exports, so it must be set before the first export.
        System.setProperty("java.rmi.server.hostname", serverHost);

        Server server = new Server(new Processor(dataset)); // zone is set during registration

        boolean started = false;
        try {
            ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(server, serverPort);

            server.registry = LocateRegistry.createRegistry(serverPort);
            server.registry.rebind(BIND_NAME, stub);

            server.registerWithProxy(proxyHost, proxyPort, serverHost, serverPort);
            started = true;
        } finally {
            // Without this a failed startup leaves the worker thread and RMI's
            // non-daemon threads running, so the JVM never exits.
            if (!started) {
                server.shutdown();
            }
            System.out.println("Server started at " + serverHost + ":" + serverPort + " and registered with proxy at " + proxyHost + ":" + proxyPort);
        }
    }


    /**
     * Announces this server to the proxy and adopts the zone number it hands back.
     * The {@link ServerInfo} must describe <em>this</em> server's registry, since the
     * proxy looks the stub up there.
     */
    public void registerWithProxy(String proxyHost, int proxyPort, String serverHost, int serverPort) throws RemoteException {


        Registry proxyRegistry = LocateRegistry.getRegistry(proxyHost, proxyPort);

        ProxyInterface proxyStub;
        try {
            proxyStub = (ProxyInterface) proxyRegistry.lookup(ProxyInterface.BINDING_NAME);
        } catch (NotBoundException e) {
            // Registry answered but nothing is bound under that name: the proxy is not up yet.
            throw new RemoteException("No proxy bound as '" + ProxyInterface.BINDING_NAME
                    + "' at " + proxyHost + ":" + proxyPort + ". Start the proxy first.", e);
        }

        this.zone = proxyStub.registerNewServer(new ServerInfo(BIND_NAME, serverHost, serverPort));
        System.out.println("Registered with proxy at " + proxyHost + ":" + proxyPort + " as zone " + zone);
    }
}
