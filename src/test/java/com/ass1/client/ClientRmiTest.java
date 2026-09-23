package com.ass1.client;

import com.ass1.common.Comparison;
import com.ass1.common.QueryResult;
import com.ass1.common.ServerInfo;
import com.ass1.proxy.ProxyInterface;
import com.ass1.server.ServerInterface;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.rmi.NoSuchObjectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Two-hop lookup against a real RMI registry started inside this JVM. */
class ClientRmiTest {

    private static final String BOUND_NAME = "test-server";

    private Registry registry;
    private FakeServer server;
    private int port;

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @BeforeEach
    void startServer() throws Exception {
        port = freePort();
        server = new FakeServer();

        ServerInterface stub = (ServerInterface) UnicastRemoteObject.exportObject(server, 0);
        registry = LocateRegistry.createRegistry(port);
        registry.rebind(BOUND_NAME, stub);
    }

    @AfterEach
    void stopServer() {
        try {
            registry.unbind(BOUND_NAME);
        } catch (Exception ignored) {
        }
        try {
            UnicastRemoteObject.unexportObject(server, true);
        } catch (NoSuchObjectException ignored) {
        }
    }

    private Query parse(String line) throws IOException {
        Path file = Files.createTempFile("queries", ".txt");
        Files.writeString(file, line + System.lineSeparator());

        Client client = new Client();
        client.readQueries(file.toString());
        Files.deleteIfExists(file);

        return client.getQueries().get(0);
    }

    private ServerInfo boundServer() {
        return new ServerInfo(BOUND_NAME, "localhost", port);
    }

    private QueryResult run(Client client, Query query, FakeProxy proxy) throws Exception {
        return client.executeQuery(query, client.getServerForQuery(query, proxy));
    }

    @Test
    void resolvesTheServerAndReturnsItsResult() throws Exception {
        Client client = new Client();
        Query query = parse("getPopulationofCountry Norway Zone:1");

        QueryResult result = run(client, query, new FakeProxy(boundServer()));

        assertEquals(5_000_000L, result.value());
        assertEquals(FakeServer.ZONE, result.serverZone());
    }

    @Test
    void passesTheClientZoneThroughToTheServer() throws Exception {
        Client client = new Client();

        run(client, parse("getPopulationofCountry Norway Zone:4"), new FakeProxy(boundServer()));

        assertEquals(4, server.lastClientZone);
    }

    @Test
    void asksTheProxyForTheQuerysOwnZone() throws Exception {
        Client client = new Client();
        FakeProxy proxy = new FakeProxy(boundServer());

        run(client, parse("getPopulationofCountry Norway Zone:3"), proxy);

        assertEquals(List.of(3), proxy.requestedZones);
    }

    @Test
    void dispatchesEachQueryTypeToItsOwnRemoteMethod() throws Exception {
        Client client = new Client();
        FakeProxy proxy = new FakeProxy(boundServer());

        run(client, parse("getPopulationofCountry Norway Zone:1"), proxy);
        run(client, parse("getNumberofCities Norway 100000 min Zone:1"), proxy);
        run(client, parse("getNumberofCountries 3 1616894 min Zone:1"), proxy);
        run(client, parse("getNumberofCountriesMM 6 1677496 4406235 Zone:1"), proxy);

        assertEquals(List.of("population", "cities", "countries", "countriesMM"), server.calls);
    }

    @Test
    void forwardsParsedArgumentsUnchanged() throws Exception {
        Client client = new Client();

        run(client, parse("getNumberofCities Norway 100000 max Zone:2"),
                new FakeProxy(boundServer()));

        assertEquals("Norway", server.lastCountry);
        assertEquals(100_000, server.lastThreshold);
        assertEquals(Comparison.MAX, server.lastComparison);
    }

    @Test
    void noRegisteredServerThrowsRemoteException() throws Exception {
        Client client = new Client();
        Query query = parse("getPopulationofCountry Norway Zone:1");

        RemoteException error = assertThrows(RemoteException.class,
                () -> client.getServerForQuery(query, new FakeProxy(null)));

        assertTrue(error.getMessage().toLowerCase().contains("no server"));
    }

    @Test
    void unknownBindingNameThrowsNotBound() throws Exception {
        Client client = new Client();
        Query query = parse("getPopulationofCountry Norway Zone:1");
        ServerInfo wrongName = new ServerInfo("no-such-server", "localhost", port);

        assertThrows(NotBoundException.class,
                () -> client.getServerForQuery(query, new FakeProxy(wrongName)));
    }

    @Test
    void unreachableRegistryThrowsRemoteException() throws Exception {
        Client client = new Client();
        Query query = parse("getPopulationofCountry Norway Zone:1");
        ServerInfo deadPort = new ServerInfo(BOUND_NAME, "localhost", freePort());

        assertThrows(RemoteException.class,
                () -> client.getServerForQuery(query, new FakeProxy(deadPort)));
    }

    private static final class FakeProxy implements ProxyInterface {
        private final ServerInfo toHandOut;
        final List<Integer> requestedZones = new ArrayList<>();

        FakeProxy(ServerInfo toHandOut) {
            this.toHandOut = toHandOut;
        }

        @Override
        public ServerInfo getServer(int zone) {
            requestedZones.add(zone);
            return toHandOut;
        }

        @Override
        public int registerNewServer(ServerInfo serverInfo) {
            return 1;
        }

        @Override
        public boolean hasRegisteredServers() {
            return toHandOut != null;
        }
    }

    private static final class FakeServer implements ServerInterface {
        static final int ZONE = 7;

        final List<String> calls = new ArrayList<>();
        volatile int lastClientZone = -1;
        volatile String lastCountry;
        volatile int lastThreshold = -1;
        volatile Comparison lastComparison;

        @Override
        public QueryResult getPopulationOfCountry(String countryName, int clientZone) {
            calls.add("population");
            lastCountry = countryName;
            lastClientZone = clientZone;
            return new QueryResult(5_000_000L, 10L, 20L, ZONE);
        }

        @Override
        public QueryResult getNumberOfCities(String countryName, int threshold,
                                             Comparison comp, int clientZone) {
            calls.add("cities");
            lastCountry = countryName;
            lastThreshold = threshold;
            lastComparison = comp;
            lastClientZone = clientZone;
            return new QueryResult(4L, 10L, 20L, ZONE);
        }

        @Override
        public QueryResult getNumberOfCountries(int cityCount, int threshold,
                                                Comparison comp, int clientZone) {
            calls.add("countries");
            lastThreshold = threshold;
            lastComparison = comp;
            lastClientZone = clientZone;
            return new QueryResult(7L, 10L, 20L, ZONE);
        }

        @Override
        public QueryResult getNumberOfCountriesMM(int cityCount, int minPopulation,
                                                  int maxPopulation, int clientZone) {
            calls.add("countriesMM");
            lastClientZone = clientZone;
            return new QueryResult(30L, 10L, 20L, ZONE);
        }

        @Override
        public int getCurrentWorkload() {
            return 0;
        }
    }
}
