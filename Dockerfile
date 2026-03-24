# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build

# Cache dependencies: Copy only pom.xml first
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline

# Build the app
COPY src ./src
RUN ./mvnw -DskipTests package

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Security: Run as a non-root user
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

# Copy the jar from builder
COPY --from=builder /build/target/*SNAPSHOT.jar app.jar

# Java performance flags for containers
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

EXPOSE 8080