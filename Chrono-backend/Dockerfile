# ---- Build Stage ----
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Nur pom.xml zuerst kopieren und Dependencies laden (Cache nutzen)
COPY pom.xml .
RUN mvn dependency:go-offline

# Jetzt Quellcode kopieren und JAR bauen
COPY src ./src
RUN mvn clean package -DskipTests

# ---- Runtime Stage (kleiner & sicherer) ----
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

# Das fertige JAR von Stage 1 kopieren
COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080
USER nonroot
ENTRYPOINT ["java","-jar","app.jar"]
