package com.ass1.server.common;

import com.ass1.common.QueryResult;

import java.util.concurrent.BlockingQueue;
import java.util.function.IntSupplier;

public class Worker implements Runnable {
    private final Cache<String, Long> cache;
    private final BlockingQueue<Task> queue;

    /**
     * Read lazily: the zone is handed out by the proxy during registration, which happens
     * after this worker is constructed, so it cannot be captured as a value here.
     */
    private final IntSupplier serverZone;

    public Worker(Cache<String, Long> cache, BlockingQueue<Task> queue, IntSupplier serverZone) {
        this.cache = cache;
        this.queue = queue;
        this.serverZone = serverZone;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Task task;
            try {
                task = queue.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                task.result.complete(processTask(task));
            } catch (Throwable error) {
                task.result.completeExceptionally(error);
            }
        }
    }

    /** Cache first, processor computation (and its full dataset scan) only on a miss. */
    private QueryResult processTask(Task task) {
        System.out.println("Processing task: " + task.getCacheKey() + " from client zone: " + task.getClientZone());
        long executionStartTime = System.currentTimeMillis();
        long waitingTimeInMs = executionStartTime - task.queuedInitialTimeInMs;

        Long cachedValue = cache.get(task.getCacheKey());
        boolean cacheHit = cachedValue != null;
        long value;

        if (cacheHit) {
            value = cachedValue;
        } else {
            value = task.getComputation().getAsLong();
            cache.put(task.getCacheKey(), value);
        }

        long executionInMs = System.currentTimeMillis() - executionStartTime;

        // The zone reported back is the zone of the server that did the work, which is what
        // the client needs in order to tell local from forwarded requests apart.
        return new QueryResult(value, executionInMs, waitingTimeInMs, serverZone.getAsInt());
    }
}
