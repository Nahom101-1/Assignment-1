package com.ass1.client;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;
import com.ass1.common.ServerInfo;
import com.ass1.proxy.ProxyInterface;
import com.ass1.server.ServerInterface;
import com.ass1.util.Args;
/**
 * Client for reading and executing statistics queries.
 */
public class Client {
    private final List<Query> queries = new ArrayList<>();

    final List<ClientResult> results =
            Collections.synchronizedList(new ArrayList<>());

    private static final int CACHE_SIZE = 45;

    private final Map<String, CacheEntry> cache =
            Collections.synchronizedMap(new LinkedHashMap<>());

    private CacheMode cacheMode = CacheMode.OFF;

    /**
     * Set from the command line, because the client has no way of asking the
     * servers whether they cache. Only affects the output file name.
     */
    private boolean serverCacheEnabled = false;

    /**
     * Address of the proxy. Set from the command line, since under Docker the
     * proxy is reached by its service name.
     */
    private String proxyHost = "localhost";
    private int proxyPort = ProxyInterface.PORT;

    /**
     * Tells the client that the servers are caching. Decides whether the output
     * goes to {@code naive_server.txt} or {@code server_cache.txt}.
     *
     * @param serverCacheEnabled true if the servers run with their cache on
     */
    public void setServerCacheEnabled(boolean serverCacheEnabled) {
        this.serverCacheEnabled = serverCacheEnabled;
    }

    /**
     * Sets the address of the proxy.
     *
     * @param proxyHost host the proxy registry runs on
     * @param proxyPort port of that registry
     */
    public void setProxy(String proxyHost, int proxyPort) {
        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
    }

    /**
     * Picks the output file for this run. If the client cache is on the run
     * counts as a client cache run, whatever the servers do.
     *
     * @return one of the three file names from the assignment
     */
    String outputFileName() {
        if (cacheMode != CacheMode.OFF) {
            return "client_cache.txt";
        }
        return serverCacheEnabled ? "server_cache.txt" : "naive_server.txt";
    }


    /**
     * Sets the eviction policy and empties the cache. The assignment says each
     * run has to start with an empty cache.
     *
     * @param cacheMode OFF, FIFO or OLDEST
     */
    public void setCacheMode(CacheMode cacheMode) {
        this.cacheMode = cacheMode;
what
        synchronized (cache) {
            cache.clear();
        }
    }

    /**
     * Looks a query up in the client cache.
     *
     * @param query the query to look for
     * @return the cached result, or null if it is not cached or caching is off
     */
    private QueryResult getCachedResult(Query query) {
        if (cacheMode == CacheMode.OFF) {
            return null;
        }

        synchronized (cache) {
            CacheEntry entry = cache.get(query.originalQuery);

            if (entry == null) {
                return null;
            }

            if (cacheMode == CacheMode.OLDEST) {
                entry.markUsed();
            }

            return entry.result;
        }
    }

    /**
     * Stores a result. If the cache is full, one entry is removed first.
     *
     * @param query  the query that produced the result
     * @param result what the server returned
     */
    private void addToCache(Query query, QueryResult result) {
        if (cacheMode == CacheMode.OFF) {
            return;
        }

        String key = query.originalQuery;

        synchronized (cache) {

            // Already cached
            if (cache.containsKey(key)) {
                return;
            }

            // Cache still has room
            if (cache.size() < CACHE_SIZE) {
                cache.put(key, new CacheEntry(result));
                return;
            }

            // Cache is full: FIFO
            if (cacheMode == CacheMode.FIFO) {
                String firstKey = cache.keySet().iterator().next();
                cache.remove(firstKey);
            }

            // Cache is full: OLDEST
            if (cacheMode == CacheMode.OLDEST) {
                String oldestKey = null;
                long oldestTime = Long.MAX_VALUE;

                for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                    if (entry.getValue().lastUsed < oldestTime) {
                        oldestTime = entry.getValue().lastUsed;
                        oldestKey = entry.getKey();
                    }
                }

                if (oldestKey != null) {
                    cache.remove(oldestKey);
                }
            }

            // There is room now
            cache.put(key, new CacheEntry(result));
        }
    }

    /**
     * Returns the queries parsed so far.
     *
     * @return the parsed queries, in input-file order
     */
    public List<Query> getQueries() {
        return queries;
    }


    /**
     * Looks up the proxy stub in the registry.
     *
     * @return a stub for the remote proxy
     * @throws RemoteException   if the registry cannot be reached
     * @throws NotBoundException if no proxy is bound under the expected name
     */
    private ProxyInterface connectToProxy() throws RemoteException, NotBoundException {

        Registry registry = LocateRegistry.getRegistry(proxyHost, proxyPort);

        return (ProxyInterface) registry.lookup(
                ProxyInterface.BINDING_NAME
        );
    }

    /**
     * Looks up a server stub using the address the proxy handed out.
     *
     * @param serverInfo the name, address and port of the server to reach
     * @return a stub for that remote server
     * @throws RemoteException   if the registry cannot be reached
     * @throws NotBoundException if no server is bound under that name
     */
    private ServerInterface connectToServer(ServerInfo serverInfo) throws RemoteException, NotBoundException {

        Registry registry = LocateRegistry.getRegistry(
                serverInfo.address,
                serverInfo.port
        );

        return (ServerInterface) registry.lookup(
                serverInfo.name
        );
    }

    /**
     * Asks the proxy which server should handle the query and connects to
     * the selected server.
     *
     * @param query the query that needs a server
     * @param proxyStub a stub for the remote proxy
     * @return a stub for the selected server
     * @throws RemoteException if no server is registered or communication
     *                         with the proxy fails
     * @throws NotBoundException if the selected server is not bound in its registry
     */
    ServerInterface getServerForQuery(
            Query query,
            ProxyInterface proxyStub
    ) throws RemoteException, NotBoundException {

        // Ask the proxy which server to use.
        ServerInfo serverInfo = proxyStub.getServer(query.zone);

        if (serverInfo == null) {
            throw new RemoteException("No server registered");
        }

        // Connect to that server.
        return connectToServer(serverInfo);
    }

    /**
     * Executes all parsed queries asynchronously with a fixed delay between
     * submitting each query.
     *
     * <p>Queries go to a thread pool, so a new query can be sent every
     * {@code interval} milliseconds without waiting for the previous one. Each
     * result is stored at the index of its query, so the result list follows the
     * input file even when requests finish out of order.</p>
     *
     * @param interval   delay in milliseconds between submitting queries
     * @throws RemoteException if communication with the proxy fails
     * @throws NotBoundException if the proxy is not registered in the RMI registry
     */
    public void executeQueries(int interval) throws RemoteException, NotBoundException {
        results.clear();

        for (int i = 0; i < queries.size(); i++) {
            results.add(null);
        }
        ProxyInterface proxy = connectToProxy();

        // Thread pool that runs the queries.
        ExecutorService executor = Executors.newCachedThreadPool();

        for (int i = 0; i < queries.size(); i++) {

            // A lambda can only use a variable that does not change, so copy the index.
            final int queryIndex = i;
            Query query = queries.get(i);

            executor.submit(() -> {
                try {
                    boolean cacheHit = false;

                    long startTime = System.currentTimeMillis();

                    QueryResult result = getCachedResult(query);

                    if (result != null) {
                        cacheHit = true;
                    } else {
                        ServerInterface server = getServerForQuery(query, proxy);

                        result = executeQuery(query, server);

                        addToCache(query, result);
                    }
                    long turnaroundTime =
                            System.currentTimeMillis() - startTime;
                    // Store the result at the index of its query.
                    ClientResult clientResult =
                            new ClientResult(query, result, turnaroundTime, cacheHit);
                    results.set(queryIndex, clientResult);
                    System.out.println(formatResult(clientResult));
                } catch (RemoteException | NotBoundException e) {
                    System.err.println(
                            "Request failed: " + e.getMessage()
                    );
                }
            });

            // Wait before submitting the next request.
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Stop taking new tasks, but let the submitted ones finish.
        executor.shutdown();

        try {
            boolean finished =
                    executor.awaitTermination(2, TimeUnit.MINUTES);

            if (!finished) {
                System.err.println(
                        "Some requests did not finish within 2 minutes. "
                                + "Writing the results collected so far."
                );
            }

            String outputFile = outputFileName();
            writeResultsToFile(outputFile);
            System.out.println("Wrote " + results.size() + " results to " + outputFile);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println(
                    "Failed to write results: " + e.getMessage()
            );
        }
    }

    /**
     * Reads an input file and parses every line into a query.
     *
     * @param filePath path to the query input file
     * @throws IOException if the file cannot be read
     */
    public void readQueries(String filePath) throws IOException {

        try (BufferedReader reader =
                     new BufferedReader(new FileReader(filePath))) {

            String line;

            while ((line = reader.readLine()) != null) {

                queries.add(parseQuery(line));
            }
        }
    }

    /**
     * Parses one input line into the matching query type.
     *
     * @param line a line of the form {@code <method> <args...> Zone:<n>}
     * @return the parsed query
     * @throws IllegalArgumentException if the method name is not recognised
     */
    private Query parseQuery(String line) {
        String originalQuery = line.trim();
        String[] parts = originalQuery.split("\\s+"); // \\s+ = one or more whitespace characters
        // Get the zone from the last element and parse it as an integer.
        int zone = Integer.parseInt(parts[parts.length - 1].replace("Zone:", ""));

        switch (parts[0]) {
            case "getPopulationofCountry": {
                String countryName = String.join(
                        " ",
                        Arrays.copyOfRange(parts, 1, parts.length - 1)
                );
                return new PopulationOfCountry(countryName, zone, originalQuery);
            }

            case "getNumberofCities": {
                Comparison compType =
                        Comparison.valueOf(parts[parts.length - 2].toUpperCase());
                int threshold = Integer.parseInt(parts[parts.length - 3]);
                String countryName = String.join(
                        " ",
                        Arrays.copyOfRange(parts, 1, parts.length - 3)
                );
                return new NumberOfCities(countryName, threshold, compType, zone, originalQuery);
            }

            case "getNumberofCountries": {
                int cityCount = Integer.parseInt(parts[1]);
                Comparison compType =
                        Comparison.valueOf(parts[parts.length - 2].toUpperCase());
                int threshold = Integer.parseInt(parts[parts.length - 3]);
                return new NumberOfCountries(cityCount, threshold, compType, zone, originalQuery);
            }

            case "getNumberofCountriesMM": {
                int cityCount = Integer.parseInt(parts[1]);
                int minPopulation = Integer.parseInt(parts[2]);
                int maxPopulation = Integer.parseInt(parts[3]);
                return new NumberOfCountriesMM(cityCount, minPopulation, maxPopulation, zone, originalQuery);
            }

            default: {
                throw new IllegalArgumentException(
                        "Method not supported: " + parts[0]
                );
            }
        }
    }

    /**
     * Invokes the remote method matching the query type.
     *
     * @param query  the query to run
     * @param server the server to run it on
     * @return the result returned by the server
     * @throws RemoteException          if the remote call fails
     * @throws IllegalArgumentException if the query type is not recognised
     */
    QueryResult executeQuery(Query query, ServerInterface server)
            throws RemoteException {

        if (query instanceof PopulationOfCountry q) {
            return server.getPopulationOfCountry(
                    q.countryName,
                    q.zone
            );
        }

        if (query instanceof NumberOfCities q) {
            return server.getNumberOfCities(
                    q.countryName,
                    q.threshold,
                    q.comp,
                    q.zone
            );
        }

        if (query instanceof NumberOfCountries q) {
            return server.getNumberOfCountries(
                    q.cityCount,
                    q.threshold,
                    q.comp,
                    q.zone
            );
        }

        if (query instanceof NumberOfCountriesMM q) {
            return server.getNumberOfCountriesMM(
                    q.cityCount,
                    q.minPopulation,
                    q.maxPopulation,
                    q.zone
            );
        }

        throw new IllegalArgumentException(
                "Unsupported query type: " + query.getClass().getSimpleName()
        );
    }

    /**
     * Line for a query that failed. The assignment wants one line per query in
     * the input file.
     */
    private String formatFailure(Query query) {
        return "FAILED " + query.originalQuery + " (request failed)";
    }

    /**
     * Formats one result for the output file:
     * {@code <result> <input query> (turnaround time: .. ms, execution time: .. ms,
     * waiting time: .. ms, processed by Server ..)}
     *
     * @param clientResult the finished query
     * @return the line, without a line break
     */
    private String formatResult(ClientResult clientResult) {
        return clientResult.result.value()
                + " " + clientResult.query.originalQuery
                + " (turnaround time: " + clientResult.turnaroundTime + " ms"
                + ", execution time: " + clientResult.result.executionTimeMs() + " ms"
                + ", waiting time: " + clientResult.result.waitingTimeMs() + " ms"
                + ", processed by Server " + clientResult.result.serverZone()
                + ")";
    }

    /**
     * Writes one line per query, a blank line, then one summary line per query
     * type.
     *
     * @param fileName file to write, overwritten if it exists
     * @throws IOException if the file cannot be written
     */
    void writeResultsToFile(String fileName) throws IOException {
        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(fileName))) {

            for (int i = 0; i < results.size(); i++) {
                ClientResult clientResult = results.get(i);

                if (clientResult != null) {
                    writer.write(formatResult(clientResult));
                } else if (i < queries.size()) {
                    writer.write(formatFailure(queries.get(i)));
                } else {
                    continue;
                }
                writer.newLine();
            }

            writer.newLine();

            // Statistics per query type.
            Map<String, QueryStats> statistics = calculateStatistics();

            // Write statistics.
            for (Map.Entry<String, QueryStats> entry : statistics.entrySet()) {
                writer.write(
                        formatStatistics(entry.getKey(), entry.getValue())
                );
                writer.newLine();
            }
        }
    }

    /**
     * Method name of a query, used to group the summary statistics. Spelled the
     * same way as in the input file.
     *
     * @param query the query to look at
     * @return the method name
     * @throws IllegalArgumentException if the query type is not recognised
     */
    private String getQueryType(Query query) {
        if (query instanceof PopulationOfCountry) {
            return "getPopulationofCountry";
        }

        if (query instanceof NumberOfCities) {
            return "getNumberofCities";
        }

        if (query instanceof NumberOfCountries) {
            return "getNumberofCountries";
        }

        if (query instanceof NumberOfCountriesMM) {
            return "getNumberofCountriesMM";
        }

        throw new IllegalArgumentException(
                "Unknown query type: " + query.getClass().getSimpleName()
        );
    }

    /**
     * Groups the results by method name and adds up the times. Failed queries
     * are skipped, so they do not affect the averages.
     *
     * @return statistics per method, in the order the methods first appear
     */
    private Map<String, QueryStats> calculateStatistics() {
        Map<String, QueryStats> stats = new LinkedHashMap<>();

        for (ClientResult clientResult : results) {
            if (clientResult == null) {
                continue;
            }

            String queryType = getQueryType(clientResult.query);

            stats.putIfAbsent(queryType, new QueryStats());

            stats.get(queryType).add(clientResult);
        }

        return stats;
    }

    /**
     * Summary line:
     * {@code <method> avg turn-around time: <A> ms, avg execution time: <B> ms,
     * avg waiting time: <C> ms, min turn-around time: <D> ms, max turn-around time: <E> ms}
     */
    private String formatStatistics(String queryType, QueryStats stats) {
        return queryType
                + " avg turn-around time: " + stats.averageTurnaround() + " ms"
                + ", avg execution time: " + stats.averageExecution() + " ms"
                + ", avg waiting time: " + stats.averageWaiting() + " ms"
                + ", min turn-around time: " + stats.minTurnaround + " ms"
                + ", max turn-around time: " + stats.maxTurnaround + " ms";
    }

    /**
     * Runs one measurement. Reads the input file, waits for the servers to
     * register, sends every query and writes the results.
     *
     * @param options command line options, listed in {@code Main}
     * @throws Exception if the input cannot be read or the proxy cannot be reached
     */
    public static void startClient(Args options) throws Exception {

        String proxyHost = options.get("proxy-host", "localhost");
        int proxyPort = options.getInt("proxy-port", ProxyInterface.PORT);
        String input = options.get("input", "data/exercise_1_input.txt");
        int interval = options.getInt("interval", 50);

        Client client = new Client();
        client.setProxy(proxyHost, proxyPort);
        client.setCacheMode(CacheMode.valueOf(
                options.get("cache", "off").trim().toUpperCase()));
        client.setServerCacheEnabled(
                Boolean.parseBoolean(options.get("server-cache", "false")));

        client.readQueries(input);
        System.out.println("Parsed " + client.getQueries().size() + " queries from " + input);

        client.awaitServers(proxyHost, proxyPort, options.getInt("wait-seconds", 60));

        System.out.println("Sending a query every " + interval + " ms, cache mode "
                + client.cacheMode + ", writing " + client.outputFileName());
        client.executeQueries(interval);
    }

    /**
     * Waits until the proxy has at least one registered server, so the client
     * does not start before the servers are up.
     *
     * @param host        proxy host
     * @param port        proxy registry port
     * @param waitSeconds how long to wait before giving up
     * @throws RemoteException if no server registers within that time
     */
    private void awaitServers(String host, int port, int waitSeconds)
            throws RemoteException, NotBoundException, InterruptedException {

        long deadline = System.currentTimeMillis() + waitSeconds * 1000L;

        while (System.currentTimeMillis() < deadline) {
            try {
                if (connectToProxy().hasRegisteredServers()) {
                    return;
                }
            } catch (RemoteException e) {
                // Proxy not up yet; keep trying until the deadline.
            }
            Thread.sleep(500);
        }

        throw new RemoteException("No server registered with the proxy at "
                + host + ":" + port + " within " + waitSeconds + " s");
    }
}

