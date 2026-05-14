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

# Seguridad: Creamos un grupo y usuario limitado
RUN addgroup -S spring && adduser -S spring -G spring

# Carpetas y Permisos
RUN mkdir -p /app/logs && chown -R spring:spring /app/logs
USER spring:spring

# Copiamos el JAR compilado desde la etapa anterior
COPY --from=build /app/target/*.jar app.jar

EXPOSE 9001

# JAVA_OPTS:
# -XX:MaxRAMPercentage=60.0: Usa el 60% de la RAM
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60.0", "-Xss512k", "-jar", "app.jar"]