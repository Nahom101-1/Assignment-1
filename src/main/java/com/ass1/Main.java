package com.ass1;

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
 * </pre>
 *
 * <p>Neither role returns: RMI keeps non-daemon threads alive once something is exported,
 * so the process stays up until it is killed.
 */
public class Main {

    public static void main(String[] argv) throws Exception {
        String mode = argv.length > 0 ? argv[0] : "";
        Args options = new Args(argv, 1);

        switch (mode) {
            case "proxy" -> Proxy.startProxy(options);
            case "server" -> Server.StartServer(options);
            default -> {
                System.err.println("""
                        Usage: java -jar solution.jar <proxy|server> [options]

                          proxy   --host <name>         address other hosts reach the proxy on (default localhost)

                          server  --server-host <name>  address this server advertises  (default localhost)
                                  --server-port <port>  this server's registry port     (default 1101)
                                  --proxy-host  <name>  where the proxy lives           (default localhost)
                                  --proxy-port  <port>  proxy registry port             (default 1099)
                                  --dataset     <path>  CSV to load
                        """);
                System.exit(2);
            }
        }
    }
}
