package com.ass1;

import com.ass1.client.Client;
import com.ass1.proxy.Proxy;
import com.ass1.server.Server;
import com.ass1.util.Args;

/**
 * Single entry point for the whole solution.
 *
 * <p>The first word selects the role, everything after it is {@code --name value} options:
 * <pre>
 *   java -jar solution.jar proxy  --host proxy
 *   java -jar solution.jar server --server-host server1 --server-port 1101 --proxy-host proxy
 *   java -jar solution.jar client --proxy-host proxy --interval 50 --cache off
 * </pre>
 *
 * <p>Proxy and server keep running until they are killed. The client stops once it
 * has written its output file.
 */
public class Main {

    public static void main(String[] argv) throws Exception {
        String mode = argv.length > 0 ? argv[0] : "";
        Args options = new Args(argv, 1);

        switch (mode) {
            case "proxy" -> Proxy.startProxy(options);
            case "server" -> Server.StartServer(options);
            case "client" -> Client.startClient(options);
            default -> {
                System.err.println("""
                        Usage: java -jar solution.jar <proxy|server|client> [options]

                          proxy   --host <name>         address other hosts reach the proxy on (default localhost)

                          server  --server-host <name>  address this server advertises  (default localhost)
                                  --server-port <port>  this server's registry port     (default 1101)
                                  --proxy-host  <name>  where the proxy lives           (default localhost)
                                  --proxy-port  <port>  proxy registry port             (default 1099)
                                  --dataset     <path>  CSV to load

                          client  --proxy-host  <name>  where the proxy lives           (default localhost)
                                  --proxy-port  <port>  proxy registry port             (default 1099)
                                  --input       <path>  query file  (default data/exercise_1_input.txt)
                                  --interval    <ms>    delay between queries, T        (default 50)
                                  --cache   off|fifo|oldest   client-side cache         (default off)
                                  --server-cache true|false   servers are caching, picks
                                                              server_cache.txt over
                                                              naive_server.txt          (default false)
                                  --output      <path>  write here instead of the
                                                        default name for the run
                                  --append  true|false  add to the output file instead
                                                        of replacing it            (default false)
                                  --wait-seconds <s>    how long to wait for servers    (default 60)
                        """);
                System.exit(2);
            }
        }
    }
}
