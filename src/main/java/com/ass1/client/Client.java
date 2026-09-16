package com.ass1.client;
import java.util.ArrayList;

/**
 * @param method name of the method
 * @param args list of arguments
 * @param zone requested zone
 */
class Query {
    String method;
    String[] args;
    int zone;

    Query(String method, String[] args, int zone){
        this.method = method;
        this.args = args;
        this.zone = zone;
    };
}

public class Client {
    public ArrayList<Query> queries = new ArrayList<>();
}
