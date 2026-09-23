package com.ass1.common;

import java.io.Serializable;

/* Dataclass that is used for passing address and port of specific host */

public class ServerInfo implements Serializable{
    private static final long serialVersionUID = 1L;

    /** Name the server is bound under in its own RMI registry. */
    public String name;
    public String address;
    public int port;

    public ServerInfo(String name, String address, int port) {
        this.name = name;
        this.address = address;
        this.port = port;
    }

    @Override
    public String toString() {
        return name + "@" + address + ":" + port;
    }
}
