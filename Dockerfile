# Single-image build for hosting: the React app is compiled and baked into the
# Spring Boot jar, so one container serves both the UI and the API.
#
# Local development still uses docker-compose.yml, where nginx serves the SPA and
# proxies /api to a separate backend. That is the shape you would want behind a CDN.
# For a hosted demo, one service is less to run and the same-origin guarantee — which
# the session cookie and CSRF token both depend on — comes for free rather than
# depending on a proxy passing the Host header correctly.
#
# Build from the repository root:  docker build -t intellidesk .

# ---- 1. Build the front end -------------------------------------------------
FROM node:22-alpine AS frontend
WORKDIR /fe

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

# ---- 2. Build the backend, with the front end inside it ---------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src

# Anything on the classpath under /static is served by Spring Boot at the web root.
COPY --from=frontend /fe/dist ./src/main/resources/static

RUN mvn -B -q package -DskipTests

# ---- 3. Runtime -------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /build/target/intellidesk-backend-*.jar app.jar
USER app

EXPOSE 8080

# preferIPv6Addresses: Railway's private network between services is IPv6-only, and
# the JVM prefers IPv4 by default — without this the backend cannot resolve the ML
# service's internal hostname.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-Djava.net.preferIPv6Addresses=true", "-jar", "/app/app.jar"]
