FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn -B clean verify

FROM gcr.io/distroless/java21-debian13:nonroot
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8081
USER nonroot
ENTRYPOINT ["java","-jar","app.jar"]
