package com.ass1.common;

import java.io.Serializable;

/**
 * Answer of a zone server for one query.
 *
 * <p>Besides the number the client asked for, the server also reports how long
 * the request waited in the queue and how long the computation itself took.
 * The assignment requires those numbers in the output file.</p>
 *
 * @param value            result of the statistics method
 * @param executionTimeMs  time the single worker thread needed to compute the result
 * @param waitingTimeMs    time the request spent in the waiting list (queue)
 * @param serverZone       zone of the server that actually did the work
 * @param cacheHit         true when the server answered from its own cache
 */
public record QueryResult(long value,
                          long executionTimeMs,
                          long waitingTimeMs,
                          int serverZone,
                          boolean cacheHit) implements Serializable {
}

