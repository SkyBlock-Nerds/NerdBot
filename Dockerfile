# Use Eclipse Temurin with Maven as the builder image
FROM maven:3-eclipse-temurin-17-alpine AS builder

# Set the working directory
WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src ./src

# Clean Maven cache and build the project with Maven
RUN mvn clean install -U -f pom.xml \
    && rm -f /app/target/original-*.jar

# Use a minimal eclipse-temurin image for running the bot
FROM eclipse-temurin:24-jdk-alpine

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the builder stage
COPY --from=builder /app/target/*.jar /app/NerdBot.jar

# Run the application
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -jar NerdBot.jar"]