# syntax=docker/dockerfile:1
# Pin both image arguments to reviewed digests in CI before publishing.
ARG JDK_IMAGE=eclipse-temurin:21-jdk-jammy
ARG JRE_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${JDK_IMAGE} AS build
WORKDIR /workspace
COPY gradlew settings.gradle* build.gradle* gradle.properties* ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null
COPY src ./src
RUN ./gradlew --no-daemon test bootJar && \
    find build/libs -type f -name '*.jar' ! -name '*-plain.jar' -exec cp '{}' /tmp/app.jar \; && \
    test -s /tmp/app.jar

FROM ${JRE_IMAGE} AS runtime
RUN apt-get update && \
    apt-get install --yes --no-install-recommends curl ca-certificates && \
    rm -rf /var/lib/apt/lists/* && \
    groupadd --system --gid 10001 app && \
    useradd --system --uid 10001 --gid app --home-dir /app app
WORKDIR /app
COPY --from=build --chown=app:app /tmp/app.jar /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
  CMD curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/readiness >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
