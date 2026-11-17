# Premisave auth-service image.
#
# Build:  docker build -t premisave-auth .
# Run:    docker run --rm -p 8080:8080 --env-file .env premisave-auth
#
# Two stages: the first builds the jar with the Maven wrapper, the second is a small Java 21 runtime.
# The same image runs everywhere (Docker Compose, Render, Coolify, Heroku, Fly.io, Railway, ...).
# What changes between development, staging and production is only the environment variables,
# above all SPRING_PROFILES_ACTIVE. See deploy/README.md.

# ---- Build -------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /build

# The Maven wrapper downloads Maven with curl and unpacks it with unzip.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl unzip ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# Dependencies first, so a code-only change does not download them again (best effort: the build below fetches anything missing).
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline || true

COPY src src
RUN ./mvnw -B -q -DskipTests package \
 && JAR="$(find target -maxdepth 1 -name '*.jar' ! -name '*.original' ! -name '*-plain.jar' | head -n 1)" \
 && test -n "$JAR" \
 && cp "$JAR" /build/app.jar

# ---- Runtime -----------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy

# curl is used by the health check. The "premisave" user runs the service.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 10001 premisave \
 && useradd --system --uid 10001 --gid premisave --home-dir /app --shell /usr/sbin/nologin premisave \
 && mkdir -p /app/logs /data \
 && chown -R premisave:premisave /app /data \
 && chmod 1777 /data /app/logs

WORKDIR /app
COPY --from=build --chown=premisave:premisave /build/app.jar /app/app.jar
COPY --chown=premisave:premisave docker/entrypoint.sh /app/entrypoint.sh
RUN sed -i 's/\r$//' /app/entrypoint.sh && chmod 0755 /app/entrypoint.sh

# Defaults that suit a container. Every one of them can be overridden by the platform.
#   SPRING_PROFILES_ACTIVE  dev, staging or prod (application-<profile>.yml)
#   JAVA_OPTS               memory is a share of the container limit, so a small plan still starts
#   STORAGE_LOCAL_PATH      where home owner documents are kept. Mount a volume or disk at /data.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    STORAGE_LOCAL_PATH=/data/homeowner-onboarding \
    TZ=UTC

EXPOSE 8080

# The service listens on PORT when the platform sets one, otherwise on 8080.
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS "http://127.0.0.1:${PORT:-8080}/health" > /dev/null || exit 1

# CMD (not ENTRYPOINT) so that Heroku, Render and Fly.io run it without special settings.
CMD ["/app/entrypoint.sh"]
