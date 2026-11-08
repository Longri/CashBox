# syntax=docker/dockerfile:1
#
# CashBox als Container-Image. Bauen:  docker build -t cashbox .
# Betrieb mit Datenbank: siehe deploy/docker-compose.yml

# ---- Build -----------------------------------------------------------------
# Läuft immer auf der Plattform des Build-Rechners (auch beim Bauen für andere
# Architekturen): Das JAR ist plattformunabhängig, Maven und Vaadin-Frontend-Build
# laufen so nativ statt in einer Emulation.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk AS build
WORKDIR /build

COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src

# Tests laufen hier nicht, sie brauchen eine MariaDB (siehe .github/workflows/docker.yml).
# vaadin-dev ist optional und landet nicht im JAR: das Image läuft im Produktionsmodus.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/root/.vaadin \
    --mount=type=cache,target=/root/.npm \
    ./mvnw -B -ntp package -DskipTests \
 && cp target/cashbox-*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Laufzeit --------------------------------------------------------------
FROM eclipse-temurin:21-jre

LABEL org.opencontainers.image.title="CashBox" \
      org.opencontainers.image.description="Verteilung des Guthabens eines Sparkontos auf virtuelle Spartöpfe" \
      org.opencontainers.image.source="https://github.com/Longri/CashBox" \
      org.opencontainers.image.licenses="Apache-2.0"

RUN groupadd --system cashbox && useradd --system --gid cashbox --no-create-home cashbox

WORKDIR /app
# Eine Schicht pro Spring-Boot-Layer: Bei einem Update ändert sich meist nur die letzte.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

USER cashbox
EXPOSE 8080

# Konfiguration über Umgebungsvariablen: DB_URL, DB_USER, DB_PASSWORD, ADMIN_USER,
# ADMIN_PASSWORD, REMEMBER_ME_DAYS (siehe deploy/.env.example)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "application.jar"]
