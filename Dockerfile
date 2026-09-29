# Production image for the Spring Boot API.
#   docker build -t hotelbooking-app .
# Built by .github/workflows/deploy.yml and pushed to GitHub Container Registry.

# ---- build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Dependencies first, so they're cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
# Tests run in the workflow's own job (they need Docker for Testcontainers), not inside the image build
RUN mvn -B -q package -DskipTests && cp target/*.jar app.jar

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 spring
COPY --from=build /build/app.jar app.jar
USER spring
EXPOSE 8080
# Heap sized from the container memory limit (set in docker-compose.prod.yml)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
