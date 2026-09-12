# Build stage — Maven and a full JDK, thrown away afterwards.
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Dependencies first, in their own layer: pom.xml changes far less often than src,
# so edits to the code don't re-download the internet on every build.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# Runtime stage — JRE only. Roughly 200MB instead of 600MB, and no build tooling
# left in the image for anyone to find.
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Don't run as root.
RUN addgroup -S app && adduser -S app -G app

COPY --from=build /build/target/intellidesk-backend-*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
