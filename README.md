# Assignment-1

Distributed query service: a proxy routes client queries to five zone servers over Java RMI.

## Running with Docker
On the root folder where the docker file is located, run:
```bash
docker compose build             # build the image only
docker compose up --build -d     # build + start proxy and 5 servers
docker compose logs -f proxy     # watch registrations
docker compose down              # stop everything
```

The image is built once and shared by all services. Rebuilds after a source edit take
seconds because Maven's dependency step is cached separately; use
`docker compose build --no-cache` if you ever need a clean rebuild.

Expected proxy output:

```
Proxy listening on proxy:1099 as 'proxy'
Registered ZoneServer at server1:1101 as zone 1
Registered ZoneServer at server2:1102 as zone 2
Registered ZoneServer at server3:1103 as zone 3
Registered ZoneServer at server4:1104 as zone 4
Registered ZoneServer at server5:1105 as zone 5
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

Run it **inside** the compose network:

```bash
docker compose run --rm client
```

It writes its output file into `data/`, next to the queue logs.

The ports are published to the host, but a host-side client still fails:

```
java.rmi.UnknownHostException: Unknown host: proxy
```

because the stub it receives says `proxy`, not `localhost`. If you must run the
client from the host, map the names first:

```bash
sudo sh -c 'echo "127.0.0.1 proxy server1 server2 server3 server4 server5" >> /etc/hosts'
```

### The three runs

The cache is selected with command-line flags, passed through these variables:

| Variable               | Values                 | Default |
|------------------------|------------------------|---------|
| `SERVER_CACHE`         | `none` `fifo` `oldest` | `none`  |
| `CLIENT_CACHE`         | `off` `fifo` `oldest`  | `off`   |
| `SERVER_CACHE_ENABLED` | `true` `false`         | `false` |
| `T`                    | milliseconds           | `50`    |

```bash
# naive_server.txt
SERVER_CACHE=none docker compose up -d --force-recreate
docker compose run --rm client

# server_cache.txt
SERVER_CACHE=fifo docker compose up -d --force-recreate
SERVER_CACHE_ENABLED=true docker compose run client

# client_cache.txt
SERVER_CACHE=none docker compose up -d --force-recreate
CLIENT_CACHE=fifo docker compose run client
```

`SERVER_CACHE` belongs on `up`, because a server fixes its cache policy at startup.
Recreate the servers between runs so the next one does not inherit a warm cache. Note
the server's own default is `fifo`; compose passes `none` explicitly so the naive run
is actually naive.

## Running without Docker

Requires Maven (`brew install maven`), then:

```bash
mvn package
java -jar target/solution.jar proxy &
java -jar target/solution.jar server --server-port 1101 --zone 1 --cache none &
java -jar target/solution.jar server --server-port 1102 --zone 2 --cache none &
```

Defaults are all `localhost`, which is correct for a single machine. Start the proxy
first.

## Options

| Role     | Option           | Default                       |
|----------|------------------|-------------------------------|
| `proxy`  | `--host`         | `localhost`                   |
| `server` | `--server-host`  | `localhost`                   |
| `server` | `--server-port`  | `1101`                        |
| `server` | `--zone`         | `0` (proxy assigns one)       |
| `server` | `--proxy-host`   | `localhost`                   |
| `server` | `--proxy-port`   | `1099`                        |
| `server` | `--dataset`      | `data/exercise_1_dataset.csv` |
| `server` | `--cache`        | `fifo`                        |
| `client` | `--interval`     | `50`                          |
| `client` | `--cache`        | `off`                         |
| `client` | `--server-cache` | `false`                       |

All roles also honour `-Djava.rmi.server.hostname=<addr>` when the explicit option is
not given.
