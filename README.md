# Assignment-1

Distributed query service: a proxy routes client queries to four zone servers over Java RMI.

## Running with Docker
On the root folder where the docker file is located, run:
```bash
docker compose build             # build the image only
docker compose up --build -d     # build + start proxy and 4 servers
docker compose logs -f proxy     # watch registrations
docker compose down              # stop everything
```

The image is built once and shared by all five services, so `docker compose build`
prints "Image ass1-solution Built" five times. Rebuilds after a source edit take
seconds because Maven's dependency step is cached separately; use
`docker compose build --no-cache` if you ever need a clean rebuild.

Expected proxy output:

```
Proxy listening on proxy:1099 as 'proxy'
Registered ZoneServer at server1:1101 as zone 1
Registered ZoneServer at server2:1102 as zone 2
Registered ZoneServer at server3:1103 as zone 3
Registered ZoneServer at server4:1104 as zone 4
```

### Why each service passes its own hostname

An RMI stub carries the address callers must dial back on, taken from
`java.rmi.server.hostname`. Each container therefore advertises its **compose service
name** (`--server-host server1`), because that is what other containers resolve.

Leaving the default `localhost` would make the proxy dial *itself* when it looks up
or polls a server.

### Startup order

`depends_on` only waits for the proxy container to *start*, not to be accepting RMI
calls. A server that starts first fails fast with a clear message, and
`restart: on-failure` retries it — that is the retry loop, so no wait script is needed.

### Running a client

Run it **inside** the compose network (see the commented `client` service in
`docker-compose.yml`). The ports are published to the host, but a host-side client
still fails:

```
java.rmi.UnknownHostException: Unknown host: proxy
```

because the stub it receives says `proxy`, not `localhost`. If you must run the
client from the host, map the names first:

```bash
sudo sh -c 'echo "127.0.0.1 proxy server1 server2 server3 server4" >> /etc/hosts'
```

## Running without Docker

Requires Maven (`brew install maven`), then:

```bash
mvn package
java -jar target/solution.jar proxy &
java -jar target/solution.jar server --server-port 1101 &
java -jar target/solution.jar server --server-port 1102 &
```

Defaults are all `localhost`, which is correct for a single machine. Start the proxy
first.

## Options

| Role     | Option          | Default                       |
|----------|-----------------|-------------------------------|
| `proxy`  | `--host`        | `localhost`                   |
| `server` | `--server-host` | `localhost`                   |
| `server` | `--server-port` | `1101`                        |
| `server` | `--proxy-host`  | `localhost`                   |
| `server` | `--proxy-port`  | `1099`                        |
| `server` | `--dataset`     | `data/exercise_1_dataset.csv` |

All roles also honour `-Djava.rmi.server.hostname=<addr>` when the explicit option is
not given.

