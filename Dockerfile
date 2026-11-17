FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src src
RUN mvn -B verify
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system budget && useradd --system --gid budget budget && mkdir -p /app/data && chown -R budget:budget /app
COPY --from=build /build/target/orcamento-voz-api-1.0.0.jar app.jar
USER budget
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
