package com.ass1;

import com.ass1.client.Client;
import com.ass1.proxy.ProxyServer;
import com.ass1.server.ServerSimulator;
import com.ass1.server.ZoneServer;
import com.ass1.util.Args;

/**
 * Single entry point of the whole assignment.
 *
 * <p>Every process of the distributed system is started from here, the first
 * word decides which part runs:</p>
 *
 * <pre>
 *   proxy       starts the load balancing (proxy) server
 *   server      starts ONE zone server and registers it at the proxy
 *   simulator   starts several zone servers inside one JVM (easy local run)
 *   client      replays the query file against the system
 *   all         proxy + 5 servers + client in one JVM (quickest demo)
 * </pre>
 */
public class Main {

    public static void main(String[] args) throws Exception {
        String mode = args.length == 0 ? "help" : args[0].toLowerCase();
        Args options = new Args(args, 1);

        switch (mode) {
            case "proxy" -> {
                // when the proxy runs alone, --port is a friendly alias for --proxy-port
                options.putIfAbsent("proxy-port", options.get("port", "1099"));
                ProxyServer.start(options);
            }
            case "server" -> ZoneServer.start(options);
            case "simulator" -> ServerSimulator.start(options);
            case "client" -> Client.start(options);
            case "all" -> {
                ProxyServer.start(options);
                ServerSimulator.start(options);
                Client.start(options);
                System.exit(0);
            }
            default -> printHelp();
        }
    }

    /** Prints the supported command lines so the program never fails silently. */
    private static void printHelp() {
        System.out.println("""
                IN5020 Assignment 1 - Java RMI International Statistics Service

                Usage: java -jar target/solution.jar <mode> [options]

                Modes and their options (defaults in brackets):

                  proxy      --port [1099] --host [localhost]
                  server     --proxy-host [localhost] --proxy-port [1099] --port [0 = free port]
                             --host [localhost] --dataset [data/exercise_1_dataset.csv]
                             --cache [none|fifo|lru] --start-delay [0] (ms, keeps zone numbers in order)
                  simulator  --servers [5] --first-port [1101] + the same options as 'server'
                  client     --input [data/exercise_1_input.txt] --output [output/naive_server.txt]
                             --delay [50] --client-cache [none|fifo|lru]
                             --proxy-host [localhost] --proxy-port [1099]
                  all        every option above, runs the full demo in one JVM

                Examples:
                  java -jar target/solution.jar proxy
                  java -jar target/solution.jar simulator --servers 5 --cache none
                  java -jar target/solution.jar client --delay 50 --output output/naive_server.txt
                  java -jar target/solution.jar all --delay 20 --cache lru --output output/server_cache.txt
                """);
    }
}
