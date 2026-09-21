package com.ass1.server;

import java.util.concurrent.CompletableFuture;
import com.ass1.common.QueryResult;

//Task object that represents a task requested by a client. 
//Object contains the String methodName e.g. getPopulationOfCountry()
//And the arguments for the required method e.g. countryName
public class Task {
    private String method; 
    private Object[] arguments;
    private CompletableFuture<QueryResult> future = new CompletableFuture<>(); //Placeholder for the QueryResult that will be produced by the worker thread.
    private long entryTime; //used for logging time                            //the RMI-call waits on this future untill the worker completes the task.

    public Task(String method, Object[] arguments){
        this.method = method;
        this.arguments = arguments;
    }

    // Get methods
    public String getMethod(){
        return method;
    }

    public Object[] getArguments(){
        return arguments;
    }

    // Getter for the future QueryResult produced by the worker thread.
    public CompletableFuture<QueryResult> getFuture(){
        return future;
    }

    //Getter and setter for calculating waiting time for queue entry.
    public long getEntryTime(){
        return entryTime;
    }

    public void setEntryTime(long entryTime){
        this.entryTime = entryTime;
    }
}

