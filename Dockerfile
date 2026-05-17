# ==========================================
# ETAPA 1: BUILD (Compilación)
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Copiamos solo el pom.xml primero para cachear las dependencias
COPY pom.xml .

# Descargamos dependencias
RUN mvn dependency:go-offline

# 2. Copiamos el código fuente y compilamos
COPY src ./src

# Compilamos el proyecto y generamos el JAR
RUN mvn package -DskipTests

# ==========================================
# ETAPA 2: RUNTIME (Ejecución)
# ==========================================
FROM eclipse-temurin:21-jre-alpine

# Instalamos fuentes y librerías gráficas para generar PDFs
RUN apk add --no-cache fontconfig ttf-dejavu

# Seguridad: grupo + usuario limitado con UID/GID fijos. Fijarlos importa para
# bind mounts: el directorio del host debe pertenecer a este UID para que el
# backend pueda escribir (ver .env.example HOST_STORAGE_DIR). Alpine `adduser -S`
# por defecto asigna UIDs del rango de sistema (100-999), inestable entre
# rebuilds; el `-u 1000` lo deja determinístico.
RUN addgroup -g 1000 -S spring && adduser -u 1000 -S spring -G spring

# Working directory de la app: todo lo del runtime vive bajo /cartones (jar,
# logs, storage de PDFs montado como volumen). Mantiene el árbol del container
# limpio bajo un único namespace del proyecto y los paths del compose alineados
# con el contrato del Dockerfile.
WORKDIR /cartones

# Carpetas y permisos. /cartones/storage queda como mount point del volumen
# (lo pre-creamos para que el chown aplique aunque el bind mount llegue después).
RUN mkdir -p /cartones/logs /cartones/storage && chown -R spring:spring /cartones
USER spring:spring

# Copiamos el JAR compilado desde la etapa anterior
COPY --from=build /app/target/*.jar app.jar

EXPOSE 9001

# JAVA_OPTS:
# -XX:MaxRAMPercentage=60.0: Usa el 60% de la RAM
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-Xss512k", "-jar", "app.jar"]