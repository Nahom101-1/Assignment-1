package com.ass1.server;

import java.util.concurrent.CompletableFuture;
import com.ass1.common.QueryResult;

public class Task {
    private String method;
    private Object[] arguments;
    private CompletableFuture<QueryResult> future = new CompletableFuture<>();

    public Task(String method, Object[] arguments){
        this.method = method;
        this.arguments = arguments;
    }

    public String getMethod(){
        return method;
    }

    public Object[] getArguments(){
        return arguments;
    }

    public CompletableFuture<QueryResult> getFuture(){
        return future;
    }
}

