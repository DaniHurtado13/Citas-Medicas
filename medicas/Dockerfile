# ETAPA 1
FROM maven:3.9-eclipse-temurin-21 AS construccion

WORKDIR /app

COPY pom.xml .

RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B package -DskipTests


# ETAPA 2
FROM eclipse-temurin:21-jre

WORKDIR /app

RUN useradd -r -s /usr/sbin/nologin appuser \
    && mkdir -p /app/almacenamiento/certificados \
    && chown -R appuser /app

COPY --from=construccion --chown=appuser /app/target/*.jar app.jar

EXPOSE 8080

USER appuser

ENTRYPOINT ["java", "-jar", "app.jar"]
