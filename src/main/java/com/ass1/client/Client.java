package com.ass1.client;

import com.ass1.cache.ResultCache;
import com.ass1.common.Query;
import com.ass1.common.QueryResult;
import com.ass1.common.ServerAddress;
import com.ass1.proxy.ProxyInterface;
import com.ass1.server.ServerInterface;
import com.ass1.util.Args;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Simulates all clients of all zones.
 *
 * <p>It reads the query file and fires one request every T milliseconds. The
 * requests do not wait for each other: every query runs in its own thread, so
 * the servers really get several requests at the same time and their waiting
 * lists grow – which is the whole point of the exercise.</p>
 */
public final class Client {

    /** Cache size demanded by the assignment for the client side cache. */
    public static final int CACHE_CAPACITY = 45;

    private final ProxyInterface proxy;
    private final ResultCache cache;
    /** Server stubs are looked up once per server and then reused. */
    private final Map<String, ServerInterface> stubs = new ConcurrentHashMap<>();

    public Client(ProxyInterface proxy, ResultCache cache) {
        this.proxy = proxy;
        this.cache = cache;
    }

    // ------------------------------------------------------------------ start-up

    /** Entry point for {@code java -jar solution.jar client ...}. */
    public static void start(Args options) throws Exception {
        Path input = Path.of(options.get("input", "data/exercise_1_input.txt"));
        Path output = Path.of(options.get("output", "output/naive_server.txt"));
        long delay = options.getInt("delay", 50);
        String proxyHost = options.get("proxy-host", "localhost");
        int proxyPort = options.getInt("proxy-port", 1099);
        // --cache belongs to the servers, the client has its own --client-cache
        ResultCache.Policy policy = ResultCache.Policy.from(options.get("client-cache", "none"));

        ProxyInterface proxy = connectToProxy(proxyHost, proxyPort);
        waitForServers(proxy, options.getInt("wait-for-servers", 60));
        Client client = new Client(proxy, new ResultCache(policy, CACHE_CAPACITY));
        client.run(input, output, delay);
    }

    /** Waits (at most {@code seconds}) until at least one zone server has registered. */
    private static void waitForServers(ProxyInterface proxy, int seconds) throws Exception {
        for (int attempt = 0; attempt < seconds * 2; attempt++) {
            // isReady() has no side effect, unlike getServer()
            if (proxy.isReady()) {
                return;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("No zone server registered at the proxy");
    }

    /** Waits until the proxy is reachable (the servers may still be starting). */
    private static ProxyInterface connectToProxy(String host, int port) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                Registry registry = LocateRegistry.getRegistry(host, port);
                return (ProxyInterface) registry.lookup(ProxyInterface.BIND_NAME);
            } catch (RemoteException | NotBoundException e) {
                last = e;
                Thread.sleep(500);
            }
        }
        throw new IllegalStateException("Proxy not reachable at " + host + ":" + port, last);
    }

    // --------------------------------------------------------------------- logic

    /** Reads the file, sends every query and writes the report. */
    public void run(Path input, Path output, long delayMs) throws Exception {
        List<Query> queries = readQueries(input);
        System.out.printf("Client: %d queries, one every %d ms, %s%n", queries.size(), delayMs, cache);

        Outcome[] outcomes = new Outcome[queries.size()];
        ExecutorService pool = Executors.newCachedThreadPool();
        long startMillis = System.currentTimeMillis();

        try {
            for (int i = 0; i < queries.size(); i++) {
                final int index = i;
                final Query query = queries.get(i);
                // execute() never throws, so a failed query cannot get lost
                pool.execute(() -> outcomes[index] = execute(query));

                // the next invocation starts T ms after this one, no matter how long
                // the current request takes
                long nextStart = startMillis + (i + 1) * delayMs;
                long sleep = nextStart - System.currentTimeMillis();
                if (sleep > 0) {
                    Thread.sleep(sleep);
                }
            }
        } finally {
            pool.shutdown();
        }
        if (!pool.awaitTermination(30, TimeUnit.MINUTES)) {
            System.err.println("Some requests did not finish in time");
            pool.shutdownNow();
        }
        long totalMs = System.currentTimeMillis() - startMillis;

        writeReport(output, outcomes);
        System.out.printf("Client finished in %d ms, report written to %s (%s)%n",
                totalMs, output.toAbsolutePath(), cache);
    }

    /** Sends one query: ask the proxy for a server, then call that server. */
    private Outcome execute(Query query) {
        try {
            long cacheStart = System.nanoTime();
            Long cached = cache.get(query.cacheKey());
            if (cached != null) {
                // answered locally, no server was involved
                long localMs = (System.nanoTime() - cacheStart) / 1_000_000;
                return new Outcome(query, cached, localMs, 0, 0, -1, null);
            }

            ServerAddress address = proxy.getServer(query.zone());
            ServerInterface server = stubFor(address);

            long startNanos = System.nanoTime();
            QueryResult result = invoke(server, query);
            long turnaroundMs = (System.nanoTime() - startNanos) / 1_000_000;

            cache.put(query.cacheKey(), result.value());
            return new Outcome(query, result.value(), turnaroundMs,
                    result.executionTimeMs(), result.waitingTimeMs(), result.serverZone(), null);
        } catch (Exception e) {
            return new Outcome(query, 0, 0, 0, 0, -1, e.toString());
        }
    }

    /** Calls the remote method that belongs to the query. */
    private QueryResult invoke(ServerInterface server, Query query) throws RemoteException {
        return switch (query.method()) {
            case Query.POPULATION_OF_COUNTRY ->
                    server.getPopulationofCountry(query.country(), query.zone());
            case Query.NUMBER_OF_CITIES ->
                    server.getNumberofCities(query.country(), query.threshold(), query.comparison(), query.zone());
            case Query.NUMBER_OF_COUNTRIES ->
                    server.getNumberofCountries(query.cityCount(), query.threshold(), query.comparison(), query.zone());
            default ->
                    server.getNumberofCountriesMM(query.cityCount(), query.threshold(), query.maxThreshold(), query.zone());
        };
    }

    /** Looks a server stub up once and keeps it for the next requests. */
    private ServerInterface stubFor(ServerAddress address) throws RemoteException, NotBoundException {
        String key = address.host() + ":" + address.port() + "/" + address.bindName();
        ServerInterface known = stubs.get(key);
        if (known != null) {
            return known;
        }
        // the lookup is a network call, so it happens outside the map
        Registry registry = LocateRegistry.getRegistry(address.host(), address.port());
        ServerInterface stub = (ServerInterface) registry.lookup(address.bindName());
        ServerInterface raced = stubs.putIfAbsent(key, stub);
        return raced != null ? raced : stub;
    }

    /** Reads the query file; a broken line is reported but does not stop the run. */
    private static List<Query> readQueries(Path input) throws IOException {
        List<Query> queries = new ArrayList<>();
        List<String> lines = Files.readAllLines(input, StandardCharsets.UTF_8);
        for (int number = 0; number < lines.size(); number++) {
            String line = lines.get(number);
            if (number == 0) {
                line = line.replace("\uFEFF", ""); // remove a byte order mark
            }
            if (line.isBlank()) {
                continue;
            }
            try {
                queries.add(Query.parse(line));
            } catch (RuntimeException e) {
                System.err.println("Skipping line " + (number + 1) + ": " + e.getMessage());
            }
        }
        return queries;
    }

    // -------------------------------------------------------------------- report

    /** Writes one line per query plus the average/min/max summary per method. */
    private void writeReport(Path output, Outcome[] outcomes) throws IOException {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        StringBuilder text = new StringBuilder();
        Map<String, Summary> perMethod = new LinkedHashMap<>();
        Summary total = new Summary();
        int answeredByClientCache = 0;
        int failed = 0;

        for (Outcome outcome : outcomes) {
            if (outcome == null) {
                failed++;
                continue;
            }
            text.append(outcome.toLine()).append(System.lineSeparator());
            perMethod.computeIfAbsent(outcome.query.method(), name -> new Summary()).add(outcome);
            total.add(outcome);
            if (outcome.error() != null) {
                failed++;
            } else if (outcome.serverZone() < 0) {
                answeredByClientCache++;
            }
        }

        text.append(System.lineSeparator());
        perMethod.forEach((method, summary) -> text.append(summary.toLine(method)).append(System.lineSeparator()));
        text.append(total.toLine("ALL QUERIES")).append(System.lineSeparator());
        text.append("client cache: ").append(cache)
                .append(", answered without contacting a server: ").append(answeredByClientCache)
                .append(", failed queries: ").append(failed)
                .append(System.lineSeparator());

        Files.writeString(output, text.toString(), StandardCharsets.UTF_8);
    }

    /** Result of one query together with its measurements. */
    private record Outcome(Query query, long value, long turnaroundMs, long executionMs,
                           long waitingMs, int serverZone, String error) {

        String toLine() {
            if (error != null) {
                return "ERROR " + query.raw() + " (" + error + ")";
            }
            String server = serverZone < 0 ? "client cache" : "Server " + serverZone;
            return value + " " + query.raw()
                    + " (turnaround time: " + turnaroundMs + " ms"
                    + ", execution time: " + executionMs + " ms"
                    + ", waiting time: " + waitingMs + " ms"
                    + ", processed by " + server + ")";
        }
    }

    /** Collects average, minimum and maximum times of one method. */
    private static final class Summary {
        private long count;
        private long turnaround;
        private long execution;
        private long waiting;
        private long minTurnaround = Long.MAX_VALUE;
        private long maxTurnaround = Long.MIN_VALUE;

        void add(Outcome outcome) {
            if (outcome.error() != null) {
                return;
            }
            count++;
            turnaround += outcome.turnaroundMs();
            execution += outcome.executionMs();
            waiting += outcome.waitingMs();
            minTurnaround = Math.min(minTurnaround, outcome.turnaroundMs());
            maxTurnaround = Math.max(maxTurnaround, outcome.turnaroundMs());
        }

        String toLine(String method) {
            if (count == 0) {
                return method + " – no successful queries";
            }
            return method
                    + " avg turn-around time: " + turnaround / count + " ms"
                    + ", avg execution time: " + execution / count + " ms"
                    + ", avg waiting time: " + waiting / count + " ms"
                    + ", min turn-around time: " + minTurnaround + " ms"
                    + ", max turn-around time: " + maxTurnaround + " ms";
        }
    }
}








