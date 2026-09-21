package com.ass1.common;

import java.io.Serializable;



public class ServerInfo implements Serializable{
    // Unique per server, since the assignment requires each server stub to be bound under its own name.
    public String name;
    public String address;
    public int port;

    public ServerInfo(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port; 
    }
}