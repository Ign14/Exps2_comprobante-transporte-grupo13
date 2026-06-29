# ---------- Etapa 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache de dependencias
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Compilacion del jar
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# ---------- Etapa 2: runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Punto de montaje de EFS dentro del contenedor
RUN mkdir -p /app/efs
ENV EFS_MOUNT_PATH=/app/efs

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
