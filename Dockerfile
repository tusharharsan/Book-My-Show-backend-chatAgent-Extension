# syntax=docker/dockerfile:1.6

# ---------- Stage 1: build the fat jar with Maven ----------
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Copy the Maven wrapper first so dependency resolution can be cached.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

# Now copy the source and build. Tests are skipped to keep deploys fast on Render's
# free build minutes — run them in CI, not on every container build.
COPY src ./src
RUN ./mvnw -B -q clean package -DskipTests

# ---------- Stage 2: minimal runtime image ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the Spring Boot fat jar produced above. The wildcard keeps this resilient
# to version bumps in pom.xml (BookMyShowOct24-0.0.1-SNAPSHOT.jar today, anything
# tomorrow).
COPY --from=build /workspace/target/*.jar app.jar

# Render injects a $PORT env var the service must bind to. Default to 8080 for
# local `docker run` so the same image works in both environments.
ENV PORT=8080
EXPOSE 8080

# `exec` so the JVM becomes PID 1 and receives SIGTERM cleanly when Render
# restarts the service. -Dserver.port reads $PORT at container start, not build.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${PORT} -jar /app/app.jar"]
