# -------- Stage 1: Build with Maven --------
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /app
# Copy pom and source
COPY pom.xml .
COPY src ./src
# Build without tests
RUN mvn clean package -DskipTests
# -------- Stage 2: Runtime with Tesseract OCR --------
FROM eclipse-temurin:17-jdk
WORKDIR /app
# Tesseract OCR and native dependencies
RUN apt-get update && \
    apt-get install -y tesseract-ocr libtesseract-dev liblept5 && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
# Copy built JAR from builder
COPY --from=builder /app/target/doc-verifier-0.0.1-SNAPSHOT.jar docverifier.jar
# Expose Spring Boot port
EXPOSE 8080
# Run Spring Boot application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar docverifier.jar"]