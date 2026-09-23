package com.ass1.server.common;

import com.ass1.common.QueryResult;

import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;

public class Task {
    private final String cacheKey;
    private final LongSupplier computation;
    private final int clientZone;
    public final long queuedInitialTimeInMs = System.currentTimeMillis();

    public final CompletableFuture<QueryResult> result = new CompletableFuture<>();

    public Task(String cacheKey, LongSupplier computation, int clientZone) {
        this.cacheKey = cacheKey;
        this.computation = computation;
        this.clientZone = clientZone;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public LongSupplier getComputation() {
        return computation;
    }

    public int getClientZone() {
        return clientZone;
    }
}
