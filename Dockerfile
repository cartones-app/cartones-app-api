# syntax=docker/dockerfile:1.7

# ==========================================
# ETAPA 1: BUILD (Compilación)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Copiamos solo el pom.xml primero para cachear las dependencias.
COPY pom.xml .

# Descarga de dependencias con BuildKit cache mount (preserva ~/.m2 entre builds).
# Reduce el tiempo de build en runs sucesivos de ~3min → ~30s en runner self-hosted.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q dependency:go-offline

# 2. Copiamos el código fuente y compilamos.
COPY src ./src

# Compilamos con cache montada (mismo target).
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q package -DskipTests

# ==========================================
# ETAPA 2: RUNTIME (Ejecución)
# ==========================================
FROM eclipse-temurin:21-jre-alpine

# Instalamos fuentes y librerías gráficas para generar PDFs.
RUN apk add --no-cache fontconfig ttf-dejavu

# Seguridad: usuario no-root.
RUN addgroup -S spring && adduser -S spring -G spring

# Carpetas y permisos.
RUN mkdir -p /app/logs && chown -R spring:spring /app/logs
USER spring:spring

# Copiamos el JAR compilado desde la etapa anterior.
COPY --from=build /app/target/*.jar app.jar

EXPOSE 9001

# -XX:MaxRAMPercentage=60.0: usa el 60% de la RAM disponible (cgroup-aware).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-Xss512k", "-jar", "app.jar"]
