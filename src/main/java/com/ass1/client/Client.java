package com.ass1.client;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;
import com.ass1.common.ServerInfo;
import com.ass1.proxy.ProxyInterface;
import com.ass1.server.ServerInterface;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.TimeUnit;

/**
 * Client for reading and executing statistics queries.
 */
public class Client {

    private final List<Query> queries = new ArrayList<>();
    final List<ClientResult> results =
            Collections.synchronizedList(new ArrayList<>());

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

        Registry registry = LocateRegistry.getRegistry(
                "localhost",
                ProxyInterface.PORT
        );

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

        // Ask the proxy which server should handle this query.
        ServerInfo serverInfo = proxyStub.getServer(query.zone);

        if (serverInfo == null) {
            throw new RemoteException("No server registered");
        }

        // Connect to the selected server and return its RMI stub.
        return connectToServer(serverInfo);
    }

    /**
     * Executes all parsed queries asynchronously with a fixed delay between
     * submitting each query.
     *
     * <p>Queries are submitted to a thread pool so that a new query can be sent
     * every {@code interval} milliseconds without waiting for the previous query
     * to finish. Results are stored using the query's original index so that the
     * final result list has the same order as the input file, even if requests
     * finish in a different order.</p>
     *
     * @param interval delay in milliseconds between submitting queries
     * @throws RemoteException if communication with the proxy fails
     * @throws NotBoundException if the proxy is not registered in the RMI registry
     */
    private void executeQueries(int interval) throws RemoteException, NotBoundException {
        results.clear();

        for (int i = 0; i < queries.size(); i++) {
            results.add(null);
        }
        ProxyInterface proxy = connectToProxy();

        // Thread pool manger
        ExecutorService executor = Executors.newCachedThreadPool();

        for (int i = 0; i < queries.size(); i++) {

            // Keep a fixed copy of the query index for this worker.
            // The loop variable i changes, but variables captured by a lambda must be final.
            final int queryIndex = i;
            Query query = queries.get(i);

            executor.submit(() -> {
                try {
                    ServerInterface server = getServerForQuery(query, proxy);
                    long startTime = System.currentTimeMillis();
                    QueryResult result = executeQuery(query, server);
                    long turnaroundTime =
                            System.currentTimeMillis() - startTime;

                    // Store the completed request at its original query position.
                    ClientResult clientResult =
                            new ClientResult(query, result, turnaroundTime);
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

        // Stop accepting new tasks while allowing submitted tasks to finish.
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

            // Written either way after a run that took minutes better than noting
            writeResultsToFile("naive_server.txt");

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
     * Line for a query that never returned, so the output still has one entry per
     * input line.
     */
    private String formatFailure(Query query) {
        return "FAILED " + query.originalQuery + " (request failed)";
    }

    /**
     * Formats one completed query as the output file expects it:
     * {@code <result> <input query> (turnaround time: .. ms, execution time: .. ms,
     * waiting time: .. ms, processed by Server ..)}
     *
     * @param clientResult the completed query
     * @return the output line, without a trailing newline
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
     * Writes one line per query, a blank separator, then one summary line per
     * query type.
     *
     * @param fileName file to create or overwrite
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

            // Calculate statistics for each query type.
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
     * Method name a query belongs to, used to group the summary statistics.
     * Spelled as in the input file, so the summary matches the queries above it.
     *
     * @param query the query to classify
     * @return the method name from the input file
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
     * Groups the completed queries by method name and totals their timings.
     * Failed queries are skipped so they cannot skew the averages.
     *
     * @return statistics per method, in the order each method first appeared
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
}
