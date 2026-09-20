# --- Build stage ---
# Uses a Maven+JDK image just to compile the app, so the final image
# doesn't need to carry Maven itself - keeps the shipped image smaller.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Downloads dependencies first, separately from copying source code.
# Docker caches each instruction as a layer - as long as pom.xml doesn't
# change, this layer is reused on rebuilds instead of re-downloading
# every dependency each time the source code changes.
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# --- Run stage ---
# A much smaller image containing only a JRE (not a full JDK) plus the
# built jar - this is what actually ships and runs in production.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/subtrack-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
