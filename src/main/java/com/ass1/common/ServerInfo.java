package com.ass1.common;

import java.io.Serializable;

/* Dataclass that is used for passing address and port of specific host */

public class ServerInfo implements Serializable{
    private static final long serialVersionUID = 1L;

    /** Name the server is bound under in its own RMI registry. */
    public String name;
    public String address;
    public int port;
    /** Zone the server declares at registration. 0 means "let the proxy assign one". */
    public int zone;

    public ServerInfo(String name, String address, int port) {
        this(name, address, port, 0);
    }

    public ServerInfo(String name, String address, int port, int zone) {
        this.name = name;
        this.address = address;
        this.port = port;
        this.zone = zone;
    }

    @Override
    public String toString() {
        return name + "@" + address + ":" + port;
    }
}
