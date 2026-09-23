package com.ass1.client;

/** How the client cache picks an entry to remove when it is full. */
public enum CacheMode {
    /** Caching is off. Every query goes to a server. */
    OFF,
    /** Remove the entry that was added first. */
    FIFO,
    /** Remove the entry that was read longest ago. */
    OLDEST
}
