# syntax=docker/dockerfile:1
#
# Societix backend (Spring Boot 3.x / Java 21) container image.
#
# Multi-stage build:
#   1. `build`   — compiles and packages the app with the Maven wrapper against a pinned
#                  Eclipse Temurin JDK 21 image (reproducible, no host Maven required).
#   2. `runtime` — runs the fat jar on a slim JRE 21 as a non-root user.
#
# Image tags are pinned (no `latest`) so builds are reproducible (design "Technology Stack").

# ----------------------------- build stage -----------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy the Maven wrapper first so the wrapper/distribution layer is cached independently
# of source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Copy sources and build the executable jar. Tests run in CI (task 5.2), not in the image
# build, to keep image builds fast and deterministic.
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# --------------------------- runtime stage ------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as an unprivileged user (never root) — least privilege for the app process.
RUN groupadd --system societix && useradd --system --gid societix --home /app societix

# The build produces exactly one application jar; copy it under a stable name.
COPY --from=build /workspace/target/societix-backend-*.jar /app/app.jar
RUN chown -R societix:societix /app
USER societix

# Spring Boot listens on 8080 by default (embedded Tomcat).
EXPOSE 8080

# SPRING_PROFILES_ACTIVE and all SOCIETIX_* settings are supplied by the environment
# (docker-compose .env); nothing sensitive is baked into the image (Req 17.5).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
