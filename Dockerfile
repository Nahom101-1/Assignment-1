# Build
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests


# Runtime
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/solution.jar ./solution.jar
COPY data ./data

ENTRYPOINT ["java", "-jar", "/app/solution.jar"]

