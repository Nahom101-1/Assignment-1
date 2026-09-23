package com.ass1.server.common;

import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded cache. A capacity of 0 turns caching off. */
public class Cache<K, V> extends LinkedHashMap<K, V> {

    public enum Policy {
        /** Remove the entry that was added first. */
        FIFO,
        /** Remove the entry that was read longest ago. */
        OLDEST;

        public static Policy of(String value) {
            try {
                return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(
                        "Unknown cache mode '" + value + "'. Use none, fifo or oldest.", e);
            }
        }
    }

    private final int maxCapacity;

    public Cache(int maxCapacity, Policy policy) {
        super(Math.max(maxCapacity, 1) + 1, 0.75f, policy == Policy.OLDEST);
        this.maxCapacity = maxCapacity;
    }

    public boolean isDisabled() {
        return maxCapacity <= 0;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > maxCapacity;
    }
}
