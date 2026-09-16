package com.ass1.proxy;

import com.ass1.common.ServerAddress;
import com.ass1.server.ServerInterface;
import com.ass1.util.Args;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The load balancing server.
 *
 * <p>Rules implemented here (straight from the assignment text):</p>
 * <ol>
 *   <li>a client of zone Z is served by the server of zone Z;</li>
 *   <li>if that zone has no server, the next zone clockwise is used;</li>
 *   <li>if the chosen server has 18 or more waiting requests, the server with the
 *       fewest waiting requests is taken; ties are broken by the shortest
 *       clockwise distance to the client;</li>
 *   <li>if every server is overloaded, the server of the client's own zone is used;</li>
 *   <li>the proxy does not ask the servers for their load on every request – only
 *       after every 18 requests it assigned to that server, and the question is
 *       asked in a background thread so nobody is blocked.</li>
 * </ol>
 */
public class ProxyServer implements ProxyInterface {

    /** A server with this many waiting requests counts as overloaded. */
    public static final int OVERLOADED_QUEUE_LENGTH = 18;
    /** After this many assignments the proxy refreshes its view of a server. */
    public static final int REFRESH_EVERY = 18;

    /** zone number -> everything the proxy knows about that server. */
    private final Map<Integer, ServerRecord> servers = new ConcurrentHashMap<>();
    private final AtomicInteger nextZone = new AtomicInteger(1);
    /** Background threads that refresh the queue lengths. */
    private final ExecutorService refreshPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "proxy-load-refresh");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public int registerServer(String host, int port, String bindName) throws RemoteException {
        int zone = nextZone.getAndIncrement();
        ServerAddress address = new ServerAddress(host, port, bindName, zone);
        try {
            Registry registry = LocateRegistry.getRegistry(host, port);
            ServerInterface stub = (ServerInterface) registry.lookup(bindName);
            servers.put(zone, new ServerRecord(address, stub));
        } catch (NotBoundException e) {
            throw new RemoteException("Could not find " + bindName + " at " + host + ":" + port, e);
        }
        System.out.println("Registered " + address);
        return zone;
    }

    @Override
    public boolean isReady() {
        return !servers.isEmpty();
    }

    @Override
    public ServerAddress getServer(int clientZone) throws RemoteException {
        if (servers.isEmpty()) {
            throw new RemoteException("No server has registered at the proxy yet");
        }

        int zones = highestZone();
        ServerRecord home = firstServerClockwiseFrom(clientZone, zones);
        ServerRecord chosen = home;

        if (home.knownQueueLength >= OVERLOADED_QUEUE_LENGTH) {
            // an overloaded server is not asked again for a while, so refresh its
            // value in the background - otherwise it would stay "overloaded" forever
            refreshIfStale(home);

            // pick the least loaded server; on a tie the one closest clockwise
            ServerRecord best = servers.values().stream()
                    .min(Comparator.<ServerRecord>comparingInt(record -> record.knownQueueLength)
                            .thenComparingInt(record -> clockwiseDistance(clientZone, record.address.zone(), zones)))
                    .orElse(home);
            // if everybody is overloaded we keep the server of the client's own zone
            chosen = best.knownQueueLength >= OVERLOADED_QUEUE_LENGTH ? home : best;
        }

        // The proxy only learns the true queue length every 18 assignments. In
        // between it estimates the load itself, so that a server it just picked
        // does not look empty for the next 17 requests.
        chosen.knownQueueLength++;
        if (chosen.assignments.incrementAndGet() % REFRESH_EVERY == 0) {
            ServerRecord toRefresh = chosen;
            refreshPool.submit(() -> refresh(toRefresh));
        }
        return chosen.address;
    }

    /** Returns the server of {@code clientZone}, or the next existing one clockwise. */
    private ServerRecord firstServerClockwiseFrom(int clientZone, int zones) {
        int start = ((clientZone - 1) % zones + zones) % zones;
        for (int step = 0; step < zones; step++) {
            ServerRecord record = servers.get(((start + step) % zones) + 1);
            if (record != null) {
                return record;
            }
        }
        // cannot happen: the map is not empty
        return servers.values().iterator().next();
    }

    /** How many zones clockwise it is from the client to the given server zone. */
    private int clockwiseDistance(int clientZone, int serverZone, int zones) {
        return ((serverZone - clientZone) % zones + zones) % zones;
    }

    private int highestZone() {
        return servers.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }

    /** Refreshes a server that has not been asked for more than a second. */
    private void refreshIfStale(ServerRecord record) {
        long now = System.currentTimeMillis();
        if (now - record.lastRefresh > 1000) {
            record.lastRefresh = now;
            refreshPool.submit(() -> refresh(record));
        }
    }

    /** Asks one server how many requests are waiting; runs in a background thread. */
    private void refresh(ServerRecord record) {
        try {
            record.knownQueueLength = record.stub.getQueueLength();
            record.lastRefresh = System.currentTimeMillis();
        } catch (RemoteException e) {
            System.err.println("Could not read the load of " + record.address + ": " + e.getMessage());
        }
    }

    /** What the proxy stores about one server. */
    private static final class ServerRecord {
        private final ServerAddress address;
        private final ServerInterface stub;
        private final AtomicInteger assignments = new AtomicInteger();
        private volatile int knownQueueLength = 0;
        private volatile long lastRefresh = System.currentTimeMillis();

        private ServerRecord(ServerAddress address, ServerInterface stub) {
            this.address = address;
            this.stub = stub;
        }
    }

    // ------------------------------------------------------------------ start-up

    /** Starts the proxy: creates the RMI registry and publishes the proxy object. */
    public static ProxyServer start(Args options) throws Exception {
        String host = options.get("host", "localhost");
        int port = options.getInt("proxy-port", 1099);
        System.setProperty("java.rmi.server.hostname", host);

        ProxyServer proxy = new ProxyServer();
        ProxyInterface stub = (ProxyInterface) UnicastRemoteObject.exportObject(proxy, 0);
        Registry registry = LocateRegistry.createRegistry(port);
        registry.rebind(ProxyInterface.BIND_NAME, stub);

        System.out.println("Proxy server ready on " + host + ":" + port);
        return proxy;
    }

    /** Small helper for logging/tests: the addresses of all registered servers. */
    public List<ServerAddress> registeredServers() {
        List<ServerAddress> addresses = new ArrayList<>();
        servers.values().forEach(record -> addresses.add(record.address));
        addresses.sort(Comparator.comparingInt(ServerAddress::zone));
        return addresses;
    }
}



