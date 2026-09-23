package com.ass1.client;

import com.ass1.common.QueryResult;

/**
 * One finished query and its result.
 *
 * <p>The client measures the turnaround time. The execution and waiting times
 * come from the server, inside {@link QueryResult}.
 */
class ClientResult {
    final Query query;
    final QueryResult result;
    final long turnaroundTime;
    final boolean cacheHit;

    /**
     * @param query          the query that was sent
     * @param result         what the server returned
     * @param turnaroundTime round-trip time in milliseconds
     * @param cacheHit       true if the client cache answered it, so no request was sent
     */
    ClientResult(Query query, QueryResult result, long turnaroundTime, boolean cacheHit) {
        this.query = query;
        this.result = result;
        this.turnaroundTime = turnaroundTime;
        this.cacheHit = cacheHit;
    }
}
