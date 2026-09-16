package com.ass1.common;

import java.io.Serializable;

/**
 * Address of a zone server, returned by the proxy to a client.
 *
 * <p>A {@code record} is Java's version of a C# record: it automatically gets a
 * constructor, getters (named {@code host()}, {@code port()} ...), equals,
 * hashCode and toString. It must implement {@link Serializable} because the
 * object travels over the network through RMI.</p>
 *
 * @param host      machine where the server's RMI registry runs
 * @param port      port of that RMI registry
 * @param bindName  name the server object is registered under
 * @param zone      geographical zone the server belongs to
 */
public record ServerAddress(String host, int port, String bindName, int zone) implements Serializable {

    @Override
    public String toString() {
        return "Server " + zone + " (" + host + ":" + port + "/" + bindName + ")";
    }
}

