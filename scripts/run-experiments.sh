#!/usr/bin/env bash
# Runs all six measurements required by the assignment:
#   naive / server cache / client cache   x   T = 50 ms and T = 20 ms
# Results land in output/, the queue logs are renamed per experiment.
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null || true)}"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
JAR=target/solution.jar
INPUT="${INPUT:-data/exercise_1_input.txt}"   # override for a quick smoke test
[ -f "$JAR" ] || scripts/build.sh

run() {
  local name=$1 delay=$2 server_cache=$3 client_cache=$4
  echo "=== $name (T=${delay} ms, server cache=$server_cache, client cache=$client_cache) ==="
  "$JAVA" -jar "$JAR" all \
      --input "$INPUT" \
      --output "output/${name}.txt" \
      --delay "$delay" \
      --cache "$server_cache" \
      --client-cache "$client_cache"
  for zone in 1 2 3 4 5; do
    [ -f "output/server_${zone}_queue.log" ] && mv "output/server_${zone}_queue.log" "output/${name}_server_${zone}_queue.log"
  done
}

run naive_server        50 none none
run server_cache        50 fifo none
run client_cache        50 none fifo
run naive_server_T20    20 none none
run server_cache_T20    20 fifo none
run client_cache_T20    20 none fifo

echo "All experiments finished, see the output/ folder."


