FROM openjdk:17-jdk-slim AS builder

WORKDIR /build

# Copy build files
COPY pom.xml .
COPY src ./src

# Build application
RUN apt-get update && \
    apt-get install -y maven && \
    mvn clean package -DskipTests && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Runtime stage
FROM openjdk:17-jdk-slim

WORKDIR /app

# Install runtime dependencies
RUN apt-get update && \
    apt-get install -y curl && \
    rm -rf /var/lib/apt/lists/*

# Copy application JAR
COPY --from=builder /build/target/*.jar app.jar

# Create non-root user
RUN groupadd -r emailservice && \
    useradd -r -g emailservice emailservice && \
    chown -R emailservice:emailservice /app

USER emailservice

# Expose ports
EXPOSE 8085 8086

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8085/actuator/health || exit 1

# JVM options
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
