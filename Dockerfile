# ============================
# Stage 1: Build
# ============================
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy wrapper and config to cache dependencies
COPY gradle/wrapper/ gradle/wrapper/
COPY gradlew .
COPY build.gradle settings.gradle ./

# Download dependencies (Cache this layer)
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Copy source code and build
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ============================
# Stage 2: Runtime
# ============================
# FROM eclipse-temurin:21-jre AS runtime
FROM ibm-semeru-runtimes:open-21-jre AS runtime
WORKDIR /app

# Create non-root user for security
RUN groupadd --system appgroup && useradd --system --gid appgroup appuser

# Copy JAR from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Change ownership of the jar
RUN chown appuser:appgroup app.jar
USER appuser

EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
