package com.ass1.client;

import java.util.Locale;

/** How the client cache picks an entry to remove when it is full. */
public enum CacheMode {
    /** Caching is off. Every query goes to a server. */
    OFF,
    /** Remove the entry that was added first. */
    FIFO,
    /** Remove the entry that was read longest ago. */
    OLDEST;

    /** Accepts "none" as well as "off", so client and server take the same words. */
    public static CacheMode of(String value) {
        String name = value.trim().toUpperCase(Locale.ROOT);
        if (name.equals("NONE")) {
            return OFF;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown cache mode '" + value + "'. Use off, fifo or oldest.", e);
        }
    }
}
