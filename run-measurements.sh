#!/usr/bin/env bash
# Runs every measurements for: naive, server FIFO, server
# OLDEST, client FIFO and client OLDEST, at T=50 and T=20.
#
#   ./run-measurements.sh
#
# Each run gets fresh servers so the caches and the queue logs start empty.
# Results go to results/<configuration>_T<interval>/.
#
# data/ holds the dataset and the query input and is bind-mounted into the
# containers, so this script only ever moves exact filenames out of it.

set -uo pipefail
cd "$(dirname "$0")"

run() {
  name=$1; server_cache=$2; client_cache=$3; cache_enabled=$4; interval=$5
  out="results/${name}_T${interval}"

  echo "### $name  T=$interval  (server=$server_cache, client=$client_cache)"

  SERVER_CACHE=$server_cache docker compose up -d --force-recreate \
      proxy server1 server2 server3 server4 server5 >/dev/null 2>&1
  sleep 12

  SERVER_CACHE=$server_cache \
  CLIENT_CACHE=$client_cache \
  SERVER_CACHE_ENABLED=$cache_enabled \
  T=$interval \
    docker compose run --rm client 2>&1 | grep -E "^Wrote|Exception"

  docker compose stop proxy server1 server2 server3 server4 server5 >/dev/null 2>&1

  mkdir -p "$out"
  for f in naive_server.txt server_cache.txt client_cache.txt; do
    [ -f "data/$f" ] && mv "data/$f" "$out/$f"
  done
  for zone in 1 2 3 4 5; do
    [ -f "data/server_${zone}_queue.csv" ] && cp "data/server_${zone}_queue.csv" "$out/"
  done
  echo
}

docker compose build

for interval in 50 20; do
  run naive         none   off    false "$interval"
  run server_fifo   fifo   off    true  "$interval"
  run server_oldest oldest off    true  "$interval"
  run client_fifo   none   fifo   false "$interval"
  run client_oldest none   oldest false "$interval"
done

docker compose down -t 3 >/dev/null 2>&1
echo "Done. Results in results/"
