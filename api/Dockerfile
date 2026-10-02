# Build context: api/
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

# Dependencies first, so editing source doesn't re-download them.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
 && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app \
 && mkdir -p /data/media && chown app:app /data/media
WORKDIR /app
COPY --from=build /src/build/libs/roomfinder-api.jar app.jar

USER app
ENV MEDIA_ROOT=/data/media \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -Duser.timezone=UTC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
