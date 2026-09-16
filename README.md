# IN5020 Assignment 1 — Java RMI International Statistics Service

**Group of 4.** A small distributed system: a **client** replays 3166 queries, a **proxy
(load balancer)** decides which **zone server** answers, and every zone server holds the
world-cities dataset, a FIFO waiting list and its own cache. As required for a group of 4,
the zone servers are **containerized with Docker** — each zone (`zone1` … `zone5`), the
proxy and the client run in their own container (see `Dockerfile` / `docker-compose.yml`
and section 5 below). This is the officially graded deployment method of this submission.

---

## 1. Quick Start Guide

### Option A: Docker (the Group-of-4 deployment — recommended)
```bash
docker compose build
docker compose up            # naive, T = 50 ms — see section 5 for all cache/delay variants
```
One container per zone server (`zone1` … `zone5`), one for the proxy, one for the client.
This is the containerized deployment required for a group of 4; see section 5 for details.

### Option B: Everything in One JVM (fastest local smoke test, no Docker)
```bash
java -jar target/solution.jar all --delay 50 --output output/naive_server.txt
```

### Option C: Separate Processes on the Local Machine (no Docker, for development)
Start in three separate terminal windows:

**Terminal 1 - Start the Proxy Server:**
```bash
java -jar target/solution.jar proxy
```

**Terminal 2 - Start the Zone Servers (`ServerSimulator` – 5 servers in one JVM, dev-only
convenience, *not* the Docker/Group-of-4 deployment):**
```bash
java -jar target/solution.jar simulator --servers 5 --cache none
```

**Terminal 3 - Start the Client:**
```bash
java -jar target/solution.jar client --delay 50 --output output/naive_server.txt
```

### Option D: One JVM per Server, No Docker (manual multi-process, for development)
Start in seven separate terminals (one proxy + five servers + one client):

**Terminal 1 - Proxy:**
```bash
java -jar target/solution.jar proxy
```

**Terminals 2-6 - Each Zone Server:**
```bash
java -jar target/solution.jar server --port 1101
java -jar target/solution.jar server --port 1102
java -jar target/solution.jar server --port 1103
java -jar target/solution.jar server --port 1104
java -jar target/solution.jar server --port 1105
```

**Terminal 7 - Client:**
```bash
java -jar target/solution.jar client --delay 50 --output output/naive_server.txt
```

### Build First (only needed for Options B–D)
```bash
scripts/build.sh
```

---

## 2. What the pieces do

| Part | Class | Job |
|------|-------|-----|
| Launcher | `com.ass1.Main` | the first word on the command line picks the mode (`proxy`, `server`, `simulator`, `client`, `all`) |
| Proxy | `com.ass1.proxy.ProxyServer` | hands out zone numbers at registration, tells each client which server to use, refreshes its load view every 18 assignments in a background thread |
| Zone server | `com.ass1.server.ZoneServer` | sleeps the simulated network delay, puts the request in the waiting list, one worker thread computes answers FIFO, records the queue length. Deployed **one per Docker container** (`zone1` … `zone5`) |
| Server simulator | `com.ass1.server.ServerSimulator` | dev-only convenience: starts 5 zone servers in one JVM without Docker, for quick local testing |
| Client | `com.ass1.client.Client` | fires one query every *T* ms in its own thread, optional client cache, writes the report |
| Statistics | `com.ass1.data.NaiveStatistics` | the "naive" implementation: re-reads the whole CSV for **every** request |
| Cache | `com.ass1.cache.ResultCache` | bounded cache with FIFO or LRU ("oldest recent used") replacement |

Rules taken literally from the assignment text:

* network delay = `80 ms` inside the zone, `80 + 30 × |zone difference| ms` across zones;
* a server with **≥ 18** waiting requests is *overloaded*;
* overloaded → least loaded server wins, ties are broken by the shortest **clockwise** distance;
  if every server is overloaded the client's own zone is used;
* a zone without a server → next zone clockwise;
* server cache = **150** entries, client cache = **45** entries;
* delay between invocations `T = 50 ms` and `T = 20 ms`; the client never waits for the previous answer.

---

## 3. Run it in IntelliJ (local, no Docker — for development only)

1. **Set the JDK**: *File → Project Structure → Project → SDK → Download JDK → Temurin 21*
   (`pom.xml` targets Java 21).
2. Open the *Maven* tool window → 🔄 **Reload All Maven Projects**.
3. Create these **Run Configurations** (*Run → Edit Configurations → + → Application*,
   main class `com.ass1.Main`, working directory = project root):

| Name | Program arguments |
|------|-------------------|
| 1 Proxy | `proxy` |
| 2 Servers | `simulator --servers 5 --cache none` |
| 3 Client | `client --delay 50 --output output/naive_server.txt` |
| Demo (all in one) | `all --delay 50 --output output/naive_server.txt` |

4. Start **1 Proxy**, then **2 Servers**, then **3 Client** — or simply run *Demo*.
   Press `⌘8` (*Services*) to watch all consoles side by side.

Note: this is a convenient way to develop/debug in an IDE; the actual Group-of-4
deliverable uses Docker (section 5).

---

## 4. Run it from the terminal (local, no Docker)

```bash
# build (works even without Maven installed)
scripts/build.sh

# everything in one JVM
java -jar target/solution.jar all --delay 50 --output output/naive_server.txt

# or as separate processes
java -jar target/solution.jar proxy &
java -jar target/solution.jar server --port 1101 &
java -jar target/solution.jar server --port 1102 &
java -jar target/solution.jar client --delay 50 --output output/naive_server.txt
```

All six required measurements in one go:

```bash
scripts/run-experiments.sh
```

---

## 5. Run it with Docker (Group-of-4 deployment — required)

```bash
docker compose build
docker compose up                                                  # naive, T = 50 ms
SERVER_CACHE=fifo OUTPUT=output/server_cache.txt docker compose up
CLIENT_CACHE=fifo OUTPUT=output/client_cache.txt docker compose up
DELAY=20 docker compose up                                         # the T = 20 ms case
```

One container per zone server (`zone1` … `zone5`), one for the proxy and one for the client —
this fulfills the assignment's "Group of 4" requirement to containerize the server code with
Docker (see the *Docker/Container* section of the assignment text). Every server passes
`--host zoneN` so that RMI publishes a name the other containers can resolve, and
`--start-delay` keeps the zone numbers in order. Results appear in `./output` on your machine.

---

## 6. Output files

`output/naive_server.txt`, `server_cache.txt`, `client_cache.txt` contain one line per query:

```
9362428 getPopulationofCountry Sweden Zone:1 (turnaround time: 120 ms, execution time: 10 ms, waiting time: 100 ms, processed by Server 1)
```

and the per-method summary at the end:

```
getPopulationofCountry avg turn-around time: 107 ms, avg execution time: 23 ms, avg waiting time: 0 ms, min turn-around time: 100 ms, max turn-around time: 146 ms
```

`output/server_<zone>_queue.log` holds `timestamp;queue_length` samples for the server graphs.

Graphs:

```bash
python3 -m pip install matplotlib
python3 scripts/plot.py output/naive_server.txt
```

---

## 7. Correctness check

The example values from the assignment text are reproduced exactly:

| Query | Expected | Produced |
|-------|----------|----------|
| `getPopulationofCountry Norway` | 3 162 856 | 3 162 856 |
| `getPopulationofCountry Sweden` | 9 362 428 | 9 362 428 |
| `getNumberofCities Norway 100000 min` | 4 | 4 |
| `getNumberofCountries 2 5000000 min` | 7 | 7 |
| `getNumberofCountriesMM 30 100000 800000` | 30 | 30 |

---

## 8. Data files

`data/exercise_1_dataset.csv` (140 574 cities, `;` separated) and `data/exercise_1_input.txt`
(3166 queries) are copied from the course archive; override them with `--dataset` / `--input`.

---

## 9. Design notes (useful for the report)

* **Threads per zone server** — many RMI threads accept calls and fill the waiting list,
  exactly **one** worker thread executes requests FIFO. The calling RMI thread blocks on a
  `CompletableFuture` until the worker is done, so the client still sees a normal remote call.
* **Where the delay is applied** — once per invocation, on the server, *before* the request
  enters the waiting list (as the assignment text describes). The reply is not delayed again.
* **Turnaround time** is measured in the client around the *server* call, not around the
  `getServer` call to the proxy — the assignment defines it as "from the moment a client gets
  to one of the servers … until it receives the final response".
* **Waiting time** is measured on the server: enqueue → start of execution.
  **Execution time**: start → result (cache lookup included).
* **Proxy load view** — the exact queue length is fetched (in a background thread) after every
  18 assignments to that server. Between two refreshes the proxy increments its own estimate
  for each assignment, otherwise a server that was just picked would look empty for 17 more
  requests and get a burst of traffic. An overloaded server whose value is older than one
  second is refreshed too, so it can never stay "overloaded" forever.
* **Zone numbers** are handed out by the proxy in registration order, so the system also works
  with fewer or more than five servers; empty zones are skipped clockwise.
* **Robust input parsing** — a line with an unknown method or a broken `Zone:` value is
  reported on the console and skipped instead of stopping the whole run.
* **Docker / Group of 4 requirement** — `Dockerfile` builds a small runtime image
  (`eclipse-temurin` + `solution.jar`); `docker-compose.yml` starts one container per zone
  server plus one for the proxy and one for the client, satisfying the assignment's
  requirement to containerize the server code for a group of 4. `ServerSimulator` remains
  in the codebase only as a lightweight, Docker-free way to test locally during development.

## 10. Suggested workload split (group of 4)

| Member | Area | Classes |
|--------|------|---------|
| 1 | Client and Proxy server | `com.ass1.client.Client`, `com.ass1.proxy.*` |
| 2 | Processing server, including queue technique | `com.ass1.server.ZoneServer`, `com.ass1.server.ServerInterface` |
| 3 | Cache technique | `com.ass1.cache.ResultCache` (server + client side) |
| 4 | Docker / container | `Dockerfile`, `docker-compose.yml`, `scripts/*.sh` |

### Java words you may not know (coming from C#)

| Java | C# equivalent |
|------|---------------|
| `interface X extends Remote`, `throws RemoteException` | a WCF/gRPC service contract |
| `record` | `record` |
| `synchronized` | `lock` |
| `ExecutorService` / `Thread` | `Task` / thread pool |
| `CompletableFuture<T>` | `Task<T>` / `TaskCompletionSource<T>` |
| `ConcurrentHashMap`, `LinkedBlockingQueue` | `ConcurrentDictionary`, `BlockingCollection` |
| `LocateRegistry` + `rebind` / `lookup` | service registration + resolving a proxy |


