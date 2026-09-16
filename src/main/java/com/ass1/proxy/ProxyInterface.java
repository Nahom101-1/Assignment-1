package com.ass1.proxy;

import com.ass1.common.ServerAddress;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface of the load balancing (proxy) server.
 *
 * <p>It is used by two kinds of callers:</p>
 * <ul>
 *   <li>zone servers call {@link #registerServer} once at start-up and get a zone number;</li>
 *   <li>clients call {@link #getServer} before every request and get the address
 *       of the server they should talk to.</li>
 * </ul>
 */
public interface ProxyInterface extends Remote {

    /** Name the proxy is published under in the RMI registry. */
    String BIND_NAME = "ProxyServer";

    /**
     * Registers a new zone server. Zone numbers are handed out in ascending order
     * (first server that registers becomes zone 1, the next zone 2 ...).
     *
     * @param host     host name / IP where the server's registry can be reached
     * @param port     port of that registry
     * @param bindName name the server object is bound to
     * @return the zone number assigned to this server
     */
    int registerServer(String host, int port, String bindName) throws RemoteException;

    /**
     * Chooses the server a client in {@code clientZone} should send its request to.
     *
     * @param clientZone zone written in the query line
     * @return address of the selected server
     */
    ServerAddress getServer(int clientZone) throws RemoteException;

    /**
     * True as soon as at least one zone server has registered. Used by the client
     * to wait for the system to come up without disturbing the load balancing.
     */
    boolean isReady() throws RemoteException;
}


