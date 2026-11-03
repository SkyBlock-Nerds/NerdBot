# Use Eclipse Temurin with Maven as the builder image
FROM maven:3-eclipse-temurin-27-alpine AS builder

# Set the working directory
WORKDIR /app

# Copy only the Maven metadata first so dependency resolution can be cached independently
COPY pom.xml .
COPY discord-framework/pom.xml discord-framework/pom.xml
COPY app/pom.xml app/pom.xml
COPY tooling/pom.xml tooling/pom.xml

# Resolve dependencies before copying source files so repeated builds can reuse the cache
RUN mvn -B -DskipTests dependency:go-offline -f pom.xml

# Copy the application source and build the project
COPY discord-framework/src discord-framework/src
COPY app/src app/src
COPY tooling/src tooling/src

RUN mvn -B clean package -f pom.xml \
    && rm -f /app/app/target/original-*.jar

# Use a minimal eclipse-temurin image for running the bot
FROM eclipse-temurin:25-jdk-alpine

# Set the working directory
WORKDIR /app

# Pass the branch name and commit hash from the build stage to the runtime stage
ARG BRANCH_NAME=unknown
ARG COMMIT_SHA=unknown
ENV BRANCH_NAME=${BRANCH_NAME}
ENV COMMIT_SHA=${COMMIT_SHA}

# Copy the built JAR file from the builder stage
COPY --from=builder /app/app/target/NerdBot.jar /app/NerdBot.jar

# Run the application
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS} -DBRANCH_NAME=${BRANCH_NAME} -DCOMMIT_SHA=${COMMIT_SHA} -jar NerdBot.jar"]
