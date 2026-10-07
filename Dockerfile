FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /src
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache su-exec \
 && addgroup -S -g 10001 app && adduser -S -u 10001 -G app -H app \
 && mkdir -p /data && chown app:app /data
WORKDIR /app
COPY --from=builder /src/build/libs/minicloud.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -Xms64m -Xmx192m -Xss512k -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=64m -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
