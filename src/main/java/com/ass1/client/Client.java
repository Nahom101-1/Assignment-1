package com.ass1.client;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;
import com.ass1.common.ServerInfo;
import com.ass1.proxy.ProxyInterface;
import com.ass1.server.ServerInterface;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Client for reading and executing statistics queries.
 */
public class Client {

    private final List<Query> queries = new ArrayList<>();

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
     * Runs one query end to end: asks the proxy which server to use, connects to it,
     * and invokes the matching remote method.
     *
     * @param query the query to run
     * @param proxyStub a stub for the remote proxy
     * @return the result returned by the chosen server
     * @throws RemoteException   if no server is registered, or the remote call fails
     * @throws NotBoundException if the chosen server is not bound in its own registry
     */
    private QueryResult executeRequest(Query query, ProxyInterface proxyStub)
            throws RemoteException, NotBoundException {

        ServerInfo serverInfo = proxyStub.getServer(query.zone);
        if(serverInfo == null){
            throw new RemoteException("No server Registered");
        }

        ServerInterface server = connectToServer(serverInfo);

        return executeQuery(query, server);
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

        String[] parts = line.split("\\s+"); // \\s+ = one or more whitespace characters
        // Get the zone from the last element and parse it as an integer.
        int zone = Integer.parseInt(parts[parts.length - 1].replace("Zone:", ""));

        switch (parts[0]) {
            case "getPopulationofCountry": {
                String countryName = String.join(
                        " ",
                        Arrays.copyOfRange(parts, 1, parts.length - 1)
                );
                return new PopulationOfCountry(countryName, zone);
            }

            case "getNumberofCities": {
                Comparison compType =
                        Comparison.valueOf(parts[parts.length - 2].toUpperCase());
                int threshold = Integer.parseInt(parts[parts.length - 3]);
                String countryName = String.join(
                        " ",
                        Arrays.copyOfRange(parts, 1, parts.length - 3)
                );
                return new NumberOfCities(countryName, threshold, compType, zone);
            }

            case "getNumberofCountries": {
                int cityCount = Integer.parseInt(parts[1]);
                Comparison compType =
                        Comparison.valueOf(parts[parts.length - 2].toUpperCase());
                int threshold = Integer.parseInt(parts[parts.length - 3]);
                return new NumberOfCountries(cityCount, threshold, compType, zone);
            }

            case "getNumberofCountriesMM": {
                int cityCount = Integer.parseInt(parts[1]);
                int minPopulation = Integer.parseInt(parts[2]);
                int maxPopulation = Integer.parseInt(parts[3]);
                return new NumberOfCountriesMM(cityCount, minPopulation, maxPopulation, zone);
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
    private QueryResult executeQuery(Query query, ServerInterface server)
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
}
