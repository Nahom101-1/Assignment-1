package com.ass1.server.common;

import com.ass1.common.QueryResult;

import java.util.concurrent.BlockingQueue;

public class Worker implements Runnable {
    private final Cache<String, Long> cache;
    private final BlockingQueue<Task> queue;
    private final Runnable queueLogger;

    public Worker(Cache<String, Long> cache, BlockingQueue<Task> queue, Runnable queueLogger) {
        this.cache = cache;
        this.queue = queue;
        this.queueLogger = queueLogger;
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            Task task;
            try {
                task = queue.take();
                queueLogger.run(); /*  Record the queue size again after the worker removes the next request. */
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

    /** Cache first, processor computation (and its simulated delay) only on a miss. */
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

        return new QueryResult(value, executionInMs, waitingTimeInMs, task.getServerZone());
    }
}
