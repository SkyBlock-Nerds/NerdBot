# Use OpenJDK 18 as the base image
FROM openjdk:18-jdk-slim AS builder

# Set the working directory
WORKDIR /app

# Copy the local project to the container
COPY . .

# Build the application using Maven
RUN apt-get update  \
    && apt-get install -y maven \
    && mvn clean install -U -f pom.xml

# Create a new image to run the application
FROM openjdk:18-jdk-slim AS runner

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the builder image
COPY --from=builder /app/target/NerdBot.jar .

# Run the application
ENTRYPOINT exec java ${JAVA_OPTS} -jar NerdBot.jar