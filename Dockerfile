FROM gradle:8.5-jdk17-alpine AS builder
WORKDIR /app
COPY . .
RUN gradle build --no-daemon

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/minicloud-*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
