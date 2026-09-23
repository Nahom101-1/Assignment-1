package com.ass1.proxy;
import java.rmi.*;
import com.ass1.common.*;


public interface ProxyInterface extends Remote{
    // Where clients and servers reach the proxy.
    int PORT = 1099;
    String BINDING_NAME = "proxy";

    // returns int so that the server can simulate the latency that was specified in the assignment text.
    ServerInfo getServer(int zone) throws RemoteException;
    // the server must already be bound under serverInfo.name, since the proxy looks it up here.
    int registerNewServer(ServerInfo serverInfo) throws RemoteException;
    // true once at least one server has registered, so the client knows it can start sending queries.
    boolean hasRegisteredServers() throws RemoteException;

}