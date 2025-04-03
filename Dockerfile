# Use OpenJDK 18 as the base image for building
FROM openjdk:18-jdk-slim AS builder

# Set the working directory
WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src ./src

# Install Maven, build the project, then clean up
RUN apt-get update \
    && apt-get install -y maven \
    && mvn clean install -U -f pom.xml \
    && apt-get remove -y maven \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/* \
    && rm -f /app/target/original-*.jar

# Use a minimal eclipse-temurin image for running the bot
FROM eclipse-temurin:24-jdk-alpine

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the builder image, excluding those prefixed with original-
COPY --from=builder /app/target/*.jar /app/

# Run the application
ENTRYPOINT exec java ${JAVA_OPTS} -jar NerdBot.jar