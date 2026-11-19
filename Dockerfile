# Production image (ADR-0006): the Vite build is copied into the Spring Boot jar's static resources, so the
# whole app is one container. Build: docker build -t fotozielnik/app . (deploy/compose.yml does this).

# ---- 1. frontend ----
FROM node:24-bookworm-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

# ---- 2. backend (with the frontend inside) ----
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /src/backend
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts backend/gradle.properties ./
COPY backend/gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet dependencies >/dev/null
COPY backend/src ./src
COPY --from=frontend /src/frontend/dist ./src/main/resources/static
RUN --mount=type=cache,target=/root/.gradle ./gradlew --no-daemon --quiet bootJar -x test \
    && cp build/libs/*-SNAPSHOT.jar /app.jar

# ---- 3. runtime ----
FROM eclipse-temurin:25-jre
RUN groupadd --system app && useradd --system --gid app --home /app app
WORKDIR /app
COPY --from=backend /app.jar /app/app.jar
USER app
EXPOSE 8080
# Heap as a share of the container limit; headless AWT for image processing.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -Djava.awt.headless=true" \
    SPRING_PROFILES_ACTIVE=prod
# The JRE image has neither curl nor wget: ask /actuator/health through bash's /dev/tcp.
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=5 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && grep -q '\"UP\"' <&3"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
