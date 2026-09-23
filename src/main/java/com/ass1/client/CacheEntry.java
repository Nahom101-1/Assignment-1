package com.ass1.client;

import com.ass1.common.QueryResult;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A cached result and a number saying when it was last read. OLDEST eviction
 * removes the entry with the smallest number.
 *
 * <p>The number comes from a counter instead of the clock. Several entries can
 * be read within the same millisecond, which would make them tie.
 */
class CacheEntry {

    private static final AtomicLong TICK = new AtomicLong();

    final QueryResult result;
    long lastUsed;

    CacheEntry(QueryResult result) {
        this.result = result;
        this.lastUsed = TICK.incrementAndGet();
    }

    /** Call this when the entry is read, so it counts as recently used. */
    void markUsed() {
        this.lastUsed = TICK.incrementAndGet();
    }
}
