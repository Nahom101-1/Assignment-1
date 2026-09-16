package com.ass1.server;

import com.ass1.cache.ResultCache;
import com.ass1.util.Args;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Starts several zone servers inside one JVM.
 *
 * <p><b>Note:</b> this project is a <i>Group of 4</i> submission, so the assignment's
 * containerization requirement is fulfilled by {@code Dockerfile}/{@code docker-compose.yml}
 * (one container per zone server, see the repository root and section 5 of the README).
 * This class only exists as a <b>convenience for local development and quick smoke
 * tests</b> — it starts several server objects on different ports of the same JVM/
 * machine, exactly like separate processes, without needing Docker running. It is
 * <i>not</i> used to satisfy the Docker/container requirement.</p>
 */
public final class ServerSimulator {

    /** Starts {@code --servers} zone servers (default 5) in the current JVM, for local testing only. */
    public static List<ZoneServer> start(Args options) throws Exception {
        int amount = options.getInt("servers", 5);
        String host = options.get("host", "localhost");
        int firstPort = options.getInt("first-port", options.getInt("port", 1101));
        String proxyHost = options.get("proxy-host", "localhost");
        int proxyPort = options.getInt("proxy-port", 1099);
        Path dataset = Path.of(options.get("dataset", "data/exercise_1_dataset.csv"));
        ResultCache.Policy policy = ResultCache.Policy.from(options.get("cache", "none"));

        List<ZoneServer> servers = new ArrayList<>();
        for (int i = 0; i < amount; i++) {
            // ports 1101, 1102, ... one RMI registry per simulated machine
            servers.add(ZoneServer.launch(host, firstPort + i, proxyHost, proxyPort, dataset, policy));
        }
        System.out.println("Server simulator started " + servers.size() + " zone servers.");
        return servers;
    }
}


