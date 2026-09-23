package com.ass1.client;

import com.ass1.common.QueryResult;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A cached result and a counter value saying when it was last read. OLDEST
 * eviction removes the entry with the smallest value.
 */
class CacheEntry {

    private static final AtomicLong TICK = new AtomicLong();

    final QueryResult result;
    long lastUsed;

    CacheEntry(QueryResult result) {
        this.result = result;
        this.lastUsed = TICK.incrementAndGet();
    }

    /** Marks the entry as read just now. */
    void markUsed() {
        this.lastUsed = TICK.incrementAndGet();
    }
}
