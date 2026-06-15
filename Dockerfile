# Stage 1: build the Spring Boot application
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY src src

RUN chmod +x ./gradlew && ./gradlew clean bootJar --no-daemon

# Stage 2: runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

COPY --from=build /workspace/build/libs/event-gateway-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENV ACCOUNT_SERVICE_URL=http://localhost:8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
