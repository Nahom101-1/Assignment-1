package com.ass1.client;

import com.ass1.common.QueryResult;

/**
 * One completed query: what was asked, what the server answered, and how long the
 * round trip took.
 *
 * <p>Turnaround is kept separately from the server's own numbers because only the
 * client can measure it.
 */
class ClientResult {
    final Query query;
    final QueryResult result;
    final long turnaroundTime;

    /**
     * @param query          the query that was sent
     * @param result         what the server returned, including its execution and waiting times
     * @param turnaroundTime round-trip time measured by the client, in milliseconds
     */
    ClientResult(Query query, QueryResult result, long turnaroundTime) {
        this.query = query;
        this.result = result;
        this.turnaroundTime = turnaroundTime;
    }
}
