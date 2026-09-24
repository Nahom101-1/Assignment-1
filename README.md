# Assignment-1

Distributed query service: a proxy routes client queries to five zone servers over
Java RMI. Design, measurements and the user guide are in the report.

## Build and run

```bash
docker compose up --build -d     # proxy and five servers
docker compose run --rm client   # one measurement
docker compose down
```

Without Docker: `mvn package`, then run `java -jar target/solution.jar` with
`proxy`, `server` or `client` as the first argument. Run it with no arguments for the
full list of options.

## The measurement runs

Cache settings are passed to Compose as variables:

| Variable | Values | Default |
|---|---|---|
| `SERVER_CACHE` | `none` `fifo` `oldest` | `none` |
| `CLIENT_CACHE` | `none` `fifo` `oldest` | `none` |
| `SERVER_CACHE_ENABLED` | `true` `false` | `false` |
| `T` | milliseconds | `50` |

`SERVER_CACHE` belongs on `up`, because a server fixes its cache policy at startup.
`SERVER_CACHE_ENABLED` only tells the client which output file name to use.

```bash
SERVER_CACHE=fifo docker compose up -d --force-recreate
SERVER_CACHE_ENABLED=true docker compose run --rm client
```

All ten runs (naive, server FIFO, server OLDEST, client FIFO, client OLDEST, each at
T=50 and T=20):

```bash
./run-measurements.sh
```

Takes about 30 minutes. Servers are recreated between runs so every cache and queue
log starts empty. Results go to `results/<configuration>_T<interval>/`, each holding
the run's output file and one queue log per server.

## Graphs

```bash
python3 -m venv .venv && .venv/bin/pip install matplotlib
.venv/bin/python plot_graphs.py
```

Writes the turn-around and queue-length figures to `results/graphs/`.

## Packaging for submission

```bash
mvn package && mkdir -p dist && cp target/solution.jar dist/
docker compose build
docker save ass1-solution:latest | gzip > dist/ass1-solution-image.tar.gz
```

`dist/` is gitignored. Load the image elsewhere with
`docker load -i dist/ass1-solution-image.tar.gz`.
