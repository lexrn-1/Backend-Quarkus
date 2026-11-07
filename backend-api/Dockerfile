FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /src
COPY . .
RUN chmod +x mvnw && ./mvnw -B -ntp -pl backend-api -DskipTests package

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=10001:10001 /src/backend-api/target/quarkus-app/ ./
USER 10001
ENV HTTP_PORT=8080
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 CMD curl -fsS "http://127.0.0.1:${HTTP_PORT}/q/health/ready" || exit 1
ENTRYPOINT ["java", "-jar", "/app/quarkus-run.jar"]
