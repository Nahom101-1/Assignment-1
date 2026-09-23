package com.ass1.client;

import com.ass1.common.QueryResult;

class ClientResult{
    final Query query;
    final QueryResult result;
    final long turnaroundTime;

    ClientResult(Query query, QueryResult result, long turnaroundTime) {
        this.query = query;
        this.result = result;
        this.turnaroundTime = turnaroundTime;
    }
}
