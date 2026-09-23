# ---- build stage: Maven lives here so the host does not need it ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first so dependency resolution is cached across source edits.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package

# ---- runtime stage: JRE only, no Maven, no sources ----
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/solution.jar ./solution.jar
COPY data ./data

# The role ("proxy" or "server") and its options come from docker-compose.
ENTRYPOINT ["java", "-jar", "/app/solution.jar"]

