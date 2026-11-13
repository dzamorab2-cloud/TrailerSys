# =====================================================================
# TrailerSys - imagen unica para produccion: API Spring Boot + frontend
# estatico (index.html, app.html, css/, js/) servidos desde el mismo
# dominio. Guia: DESPLIEGUE.md
# =====================================================================

# ---------- 1) Compilar el backend ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
# Las pruebas se corren antes de desplegar (ver DESPLIEGUE.md); aqui se
# omiten para que la imagen se construya rapido.
RUN mvn -q -B -DskipTests package && cp target/*.jar /build/app.jar

# ---------- 2) Imagen final ----------
FROM eclipse-temurin:21-jre-noble

# pg_dump 18 (debe ser >= a la version del servidor PostgreSQL) para el
# modulo de Respaldos.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && install -d /usr/share/postgresql-common/pgdg \
 && curl -fsSL -o /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
      https://www.postgresql.org/media/keys/ACCC4CF8.asc \
 && echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt noble-pgdg main" \
      > /etc/apt/sources.list.d/pgdg.list \
 && apt-get update \
 && apt-get install -y --no-install-recommends postgresql-client-18 \
 && rm -rf /var/lib/apt/lists/*

RUN useradd --system --create-home --home-dir /app trailersys
WORKDIR /app

COPY --from=build /build/app.jar /app/app.jar
COPY index.html app.html /app/frontend/
COPY css /app/frontend/css
COPY js /app/frontend/js

RUN mkdir -p /app/backups && chown -R trailersys:trailersys /app
USER trailersys

ENV SPRING_PROFILES_ACTIVE=prod \
    FRONTEND_DIR=/app/frontend \
    BACKUPS_DIR=/app/backups \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

EXPOSE 8080
VOLUME ["/app/backups"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
  CMD curl -fsS http://localhost:${PORT:-8080}/api/auth/config || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
