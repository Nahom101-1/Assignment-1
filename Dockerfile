# ---------------------------------------------------------------------------
# Stage 1: compile the Java sources (no Maven needed, plain javac + jar)
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY src ./src
RUN find src/main/java -name "*.java" > sources.txt \
 && mkdir -p classes \
 && javac --release 21 -d classes @sources.txt \
 && jar --create --file solution.jar --main-class com.ass1.Main -C classes .

# ---------------------------------------------------------------------------
# Stage 2: the small runtime image that is actually started
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/solution.jar ./solution.jar
COPY data ./data
RUN mkdir -p /app/output

# The mode (proxy / server / client) and its options are given by docker compose.
ENTRYPOINT ["java", "-jar", "/app/solution.jar"]
CMD ["--help"]

