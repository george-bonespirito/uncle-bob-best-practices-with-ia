# Build stage
FROM gradle:8.10-jdk21 AS builder

WORKDIR /app

RUN mkdir -p /home/gradle/.gradle && chown -R 1000:1000 /home/gradle/.gradle /app

COPY gradle/ gradle/
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .
COPY gradle/libs.versions.toml gradle/libs.versions.toml

COPY order/build.gradle.kts order/
COPY notifications/build.gradle.kts notifications/

RUN ./gradlew dependencies --no-daemon

COPY order/src order/src
COPY notifications/src notifications/src

RUN ./gradlew :order:bootJar --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -g 1000 -S appgroup && \
    adduser -u 1000 -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /app/order/build/libs/order-*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser:appgroup

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]