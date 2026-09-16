package com.ass1.cache;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Small bounded cache for query results.
 *
 * <p>Two replacement policies are required by the assignment:</p>
 * <ul>
 *   <li>{@code FIFO} – the entry inserted first is dropped first;</li>
 *   <li>{@code LRU} – the entry that was not used for the longest time is dropped
 *       (the assignment calls it "oldest recent used").</li>
 * </ul>
 *
 * <p>Both are one line of work in Java: {@link LinkedHashMap} keeps either the
 * insertion order or the access order and lets us drop the oldest entry by
 * overriding {@code removeEldestEntry}. All methods are {@code synchronized}
 * because several RMI threads may touch the cache at the same time.</p>
 */
public final class ResultCache {

    /** Replacement strategy, {@code NONE} switches the cache off completely. */
    public enum Policy {
        NONE, FIFO, LRU;

        /** Reads the policy from a command line value such as "lru"; unknown text means NONE. */
        public static Policy from(String text) {
            if (text == null) {
                return NONE;
            }
            return switch (text.trim().toLowerCase()) {
                case "fifo", "true", "on", "yes" -> FIFO;
                case "lru", "oldest" -> LRU;
                default -> NONE;
            };
        }
    }

    private final Policy policy;
    private final int capacity;
    private final Map<String, Long> entries;
    private long hits;
    private long misses;

    public ResultCache(Policy policy, int capacity) {
        this.policy = policy;
        this.capacity = capacity;
        boolean accessOrder = policy == Policy.LRU;
        this.entries = new LinkedHashMap<>(16, 0.75f, accessOrder) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                return size() > ResultCache.this.capacity;
            }
        };
    }

    /** True when this cache actually stores anything. */
    public boolean isEnabled() {
        return policy != Policy.NONE;
    }

    /**
     * Looks up a stored result.
     *
     * @return the cached value, or {@code null} when the value is unknown
     */
    public synchronized Long get(String key) {
        if (!isEnabled()) {
            return null;
        }
        Long value = entries.get(key);
        if (value == null) {
            misses++;
        } else {
            hits++;
        }
        return value;
    }

    /** Stores a result, dropping the oldest entry when the cache is full. */
    public synchronized void put(String key, long value) {
        if (isEnabled()) {
            entries.put(key, value);
        }
    }

    public synchronized long hits() {
        return hits;
    }

    public synchronized long misses() {
        return misses;
    }

    public synchronized int size() {
        return entries.size();
    }

    public Policy policy() {
        return policy;
    }

    @Override
    public synchronized String toString() {
        return "cache[" + policy + ", size=" + entries.size() + "/" + capacity
                + ", hits=" + hits + ", misses=" + misses + "]";
    }
}

