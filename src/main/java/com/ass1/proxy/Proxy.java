package com.ass1.proxy;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.ass1.common.ServerInfo;
import com.ass1.server.ServerInterface;
import com.ass1.util.Args;

/**
 * Routes client queries to zone servers. 
 * Does not extend UnicastRemoteobject because its constructor would export the proxy before the fields are set. 
 * The proxy is exported in startProxy instead.
 */
public class Proxy implements ProxyInterface{

    /** Keeps the registry reachable: an unreferenced registry can be garbage collected. */
    private Registry registry;

    static class RegisteredServer{
        final ServerInfo serverInfo;
        final ServerInterface stub;
        final int serverZone;
        // Assignments since the last poll.
        int requestsSinceLastPoll;
        // length of waiting list from last poll, not live data
        int waitingList;

        // What the proxy stores about each server
        RegisteredServer(ServerInfo serverInfo, ServerInterface stub, int serverZone) {
            this.serverInfo = serverInfo;
            this.stub = stub;
            this.serverZone = serverZone;
        }
    }

    int nextZone = 1;
    ArrayList<RegisteredServer> registeredServers = new ArrayList<RegisteredServer>();

    static final int OVERLOAD_THRESHOLD = 18;
    static final int POLL_INTERVAL = 18;

    private final ExecutorService workloadPoller = Executors.newCachedThreadPool();

    // Home zone's server unless overloaded, then least loaded elsewhere, ties by clockwise distance.
    public synchronized ServerInfo getServer(int zone) {
        if (registeredServers.isEmpty()) {
            return null;
        }

        int highestZone = highestZone();
        RegisteredServer homeServer = firstServerClockwiseFrom(zone, highestZone);
        int homeZone = homeServer.serverZone;

        RegisteredServer chosen = homeServer;
        if (homeServer.waitingList >= OVERLOAD_THRESHOLD) {
            RegisteredServer best = null;
            int bestDistance = 0;
            for (RegisteredServer candidate : registeredServers) {
                if (candidate == homeServer) {
                    continue;
                }
                int distance = (candidate.serverZone - homeZone + highestZone) % highestZone;
                boolean better = best == null
                        || candidate.waitingList < best.waitingList
                        || (candidate.waitingList == best.waitingList && distance < bestDistance);
                if (better) {
                    best = candidate;
                    bestDistance = distance;
                }
            }
            // Keep the home server when every other one is overloaded too.
            if (best != null && best.waitingList < OVERLOAD_THRESHOLD) {
                chosen = best;
            }
        }

        countRequest(chosen);
        return chosen.serverInfo;
    }

    // size of the zone ring zones run 1..highestZone, and a zone may have no server.
    private int highestZone() {
        int highest = 0;
        for (RegisteredServer server : registeredServers) {
            highest = Math.max(highest, server.serverZone);
        }
        return highest;
    }

    // check if there is server in given zone
    private RegisteredServer serverInZone(int zone) {
        for (RegisteredServer server : registeredServers) {
            if (server.serverZone == zone) {
                return server;
            }
        }
        return null;
    }

    // The requested zone's server, or the next zone clockwise that has one.
    private RegisteredServer firstServerClockwiseFrom(int zone, int highestZone) {
        int start = (zone >= 1 && zone <= highestZone) ? zone : 1;
        for (int step = 0; step < highestZone; step++) {
            RegisteredServer server = serverInZone((start - 1 + step) % highestZone + 1);
            if (server != null) {
                return server;
            }
        }
        return registeredServers.get(0);
    }

    // Caller must hold the lock. Polls the server in the background every POLL_INTERVAL requests.
    private void countRequest(RegisteredServer server) {
        server.requestsSinceLastPoll++;
        if (server.requestsSinceLastPoll >= POLL_INTERVAL) {
            server.requestsSinceLastPoll = 0;
            workloadPoller.submit(() -> refreshWorkload(server));
        }
    }

    // Runs on a poller thread so a slow server never blocks routing.
    private void refreshWorkload(RegisteredServer server) {
        try {
            int workload = server.stub.getCurrentWorkload();
            synchronized (this) {
                server.waitingList = workload;
            }
        } catch (Exception e) {
            // Catch everything so that the error doesnt disappear 
            System.err.println("Workload poll failed for " + server.serverInfo.name + ": " + e);
        }
    }

    // Looks the server up before taking a zone, so a bad address is rejected without reserving the zone number. 
    public int registerNewServer(ServerInfo serverInfo) throws RemoteException {
        ServerInterface stub;
        try {
            Registry registry = LocateRegistry.getRegistry(serverInfo.address, serverInfo.port);
            stub = (ServerInterface) registry.lookup(serverInfo.name);
        } catch (NotBoundException e) {
            throw new RemoteException("No server bound as '" + serverInfo.name + "' at " + serverInfo.address + ":" + serverInfo.port, e);
        }

        // synchronized because RMI calls arrive on separate threads.
        synchronized (this) {
            // A server that declares its own zone keeps it, if not it gets assigned the zone after the current highest zone. . 
            int assignedZone = serverInfo.zone > 0 ? serverInfo.zone : nextZone;
            nextZone = Math.max(nextZone, assignedZone + 1);

            RegisteredServer newServer = new RegisteredServer(serverInfo, stub, assignedZone);
            registeredServers.add(newServer);
            System.out.println("Registered " + serverInfo.name + " at " + serverInfo.address + ":" + serverInfo.port + " as zone " + newServer.serverZone);
            return newServer.serverZone;
        }
    }

    // Clients can check if there are any servers before asking for one. 
    public synchronized boolean hasRegisteredServers() {
        return !registeredServers.isEmpty();
    }

    // Creates registry on port 1099 and binds the proxy in it
    public static Proxy startProxy(Args options) throws RemoteException {
        // use --host or rmi server hostname or localhost if not set 
        String hostname = options.get("host",
                System.getProperty("java.rmi.server.hostname", "localhost"));

        // Written into the proxy's stub, so it must be reachable by every caller. Set before exporting.
        System.setProperty("java.rmi.server.hostname", hostname);

        Proxy proxy = new Proxy();

        // Exported on the registry's port, so Docker only has to publish that one port.
        ProxyInterface stub = (ProxyInterface) UnicastRemoteObject.exportObject(proxy, PORT);

        proxy.registry = LocateRegistry.createRegistry(PORT);
        proxy.registry.rebind(BINDING_NAME, stub);

        System.out.println("Proxy listening on " + hostname + ":" + PORT + " as '" + BINDING_NAME + "'");
        return proxy;
    }
}