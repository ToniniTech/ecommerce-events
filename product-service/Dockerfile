# ─── Stage 1: Build ────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Install Maven
RUN apk add --no-cache maven

# Cache dependencies first (Docker layer cache optimization)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Build the application (tests are skipped: contextLoads needs a live DB)
COPY src ./src
RUN mvn clean package -DskipTests -B

# ─── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Security: run as non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy jar from builder stage
COPY --from=builder /app/target/product-service-*.jar app.jar

EXPOSE 8085

# Health endpoint provided by spring-boot-starter-actuator
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8085/actuator/health || exit 1

ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:+UseG1GC", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
