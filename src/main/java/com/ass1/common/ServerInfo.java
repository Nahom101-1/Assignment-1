package com.ass1.common;

import java.io.Serializable;

/* Dataclass that is used for passing address and port of specific host */

public class ServerInfo implements Serializable{
    public String address;     
    public int port; 

    public ServerInfo(String address, int port) {
        this.address = address; 
        this.port = port; 
    }
}
