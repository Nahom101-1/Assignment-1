#!/usr/bin/env bash
# Compiles the project without Maven (handy if `mvn` is not installed) and
# builds a runnable jar in target/solution.jar
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null || true)}"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"

rm -rf target/classes
mkdir -p target/classes output
find src/main/java -name "*.java" > target/sources.txt
"$JAVAC" --release 21 -d target/classes @target/sources.txt
"$JAR" --create --file target/solution.jar --main-class com.ass1.Main -C target/classes .

echo "Built target/solution.jar"

