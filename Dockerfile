# One Dockerfile for both services (multi-module Maven build):
#   docker build --build-arg MODULE=claims-service --build-arg JAR=claims-service-1.0.0.jar .
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY claim-events/pom.xml claim-events/
COPY adjudication-service/pom.xml adjudication-service/
COPY claims-service/pom.xml claims-service/
RUN mvn -B -q -pl ${MODULE} -am dependency:go-offline -DskipTests || true
COPY . .
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
ARG JAR
RUN useradd --create-home app
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${JAR} app.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
