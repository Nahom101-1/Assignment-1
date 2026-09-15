package com.ass1.proxy;
import java.rmi.*; 
import com.ass1.common.*;


public interface ProxyInterface extends Remote{
    ServerInfo getServer(int zone); 
    // returns int so that the server can simulate the latency that was specified in the assignment text. 
    int registerNewServer(ServerInfo serverInfo); 

}


