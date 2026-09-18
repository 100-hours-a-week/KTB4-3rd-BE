FROM eclipse-temurin:25-jdk-jammy AS builder

WORKDIR /workspace

# Copy build metadata first so dependency resolution can be cached independently
# from application source changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon help

COPY src ./src

ARG BUILD_SHA=""
ARG RELEASE_VERSION=""
ARG BUILD_DATE=""

RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar

# Runtime stage: keep the JDK and Gradle out of the final image.
FROM eclipse-temurin:25-jre-jammy AS runner

WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app app

ARG BUILD_SHA=""
ARG RELEASE_VERSION=""
ARG BUILD_DATE=""

LABEL org.opencontainers.image.revision="${BUILD_SHA}" \
      org.opencontainers.image.version="${RELEASE_VERSION}" \
      org.opencontainers.image.created="${BUILD_DATE}"

# application-prod.yaml is the runtime configuration for the container.
# Database values are injected by the deployment environment, never baked into
# the image.
ENV JAVA_OPTS="" \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

COPY --from=builder --chown=app:app /workspace/build/libs/*.jar /app/app.jar

USER app

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
