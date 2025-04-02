# Use OpenJDK 18 as the base image
FROM openjdk:18-jdk-slim AS builder

# Set the working directory
WORKDIR /app

# Copy the local project to the container
COPY pom.xml .
COPY src ./src

# Build the application using Maven
RUN apt-get update \
    && apt-get install -y maven \
    && mvn clean install -U -f pom.xml \
    && apt-get remove -y maven \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*

# Create a new image to run the application
FROM openjdk:18-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the builder image
COPY --from=builder /app/target/*.jar /app/
RUN find /app -name "*.jar" ! -name "original-*.jar" -exec mv {} /app/NerdBot.jar \;

# Run the application
ENTRYPOINT exec java ${JAVA_OPTS} -jar NerdBot.jar