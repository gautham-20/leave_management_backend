# Multi-stage build for the Spring Boot API on Railway.
# Stage 1 builds with Maven; stage 2 ships only the JRE and the fat jar.

# --- Build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies are copied first so the layer is cached and only re-resolved
# when the POM changes, not on every source edit.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Runtime ---
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Run as a non-root user.
RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

COPY --from=build /build/target/leave-management-api.jar app.jar

EXPOSE 8080

# Honour Railway's dynamic PORT, defaulting to 8080 locally.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV PORT=8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]