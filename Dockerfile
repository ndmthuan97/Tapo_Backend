# ── Stage 1: Build ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Cache dependencies: copy POM first (layer cache optimization)
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Build application (skip tests — run separately in CI)
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ───────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

LABEL org.opencontainers.image.title="Tapo Backend"
LABEL org.opencontainers.image.source="https://github.com/your-org/tapo"

WORKDIR /app

# Non-root user for security (java-pro: never run as root in production)
RUN addgroup -S tapo && adduser -S tapo -G tapo
USER tapo

# Copy built artifact from builder
COPY --from=builder /app/target/*.jar app.jar

# JVM flags: ZGC for low-latency GC, max RAM 75% of container limit
# java-pro: UseZGC preferred for web apps (low pause time vs G1)
ENV JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
