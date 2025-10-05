# Use Eclipse Temurin with Maven as the builder image
FROM maven:3-eclipse-temurin-25-alpine AS builder

# Set the working directory
WORKDIR /app

# Copy the project files
COPY pom.xml .
COPY src ./src

# Clean Maven cache and build the project with Maven
RUN mvn clean install -f pom.xml \
    && rm -f /app/target/original-*.jar

# Use a minimal eclipse-temurin image for running the bot
FROM eclipse-temurin:25-jdk-alpine

# Set the working directory
WORKDIR /app

# Pass the branch name from the build stage to the runtime stage
ARG BRANCH_NAME=unknown
ENV BRANCH_NAME=${BRANCH_NAME}

# Copy the built JAR file from the builder stage
COPY --from=builder /app/target/*.jar /app/NerdBot.jar

# Run the application
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -DBRANCH_NAME=${BRANCH_NAME} -jar NerdBot.jar"]