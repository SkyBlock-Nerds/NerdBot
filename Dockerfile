# Use OpenJDK 17 as the base image
FROM openjdk:17-jdk-slim AS builder

# Set the working directory
WORKDIR /app

# Arguments for Git repository information
ARG GITHUB_USERNAME
ARG GITHUB_TOKEN
ENV REPO_USERNAME=TheMGRF
ENV REPO_NAME=NerdBot
ENV REPO_BRANCH=master
ENV SOURCE_CODE_DIR=repository
ENV JAR_FILE_NAME=NerdBot.jar

# Clone the Git repository
RUN apt-get update && apt-get install -y maven git zip unzip \
    && git clone https://${GITHUB_USERNAME}:${GITHUB_TOKEN}@github.com/${REPO_USERNAME}/${REPO_NAME}.git -b ${REPO_BRANCH} ${SOURCE_CODE_DIR}

# Set the working directory to the Git directory
WORKDIR /app/${SOURCE_CODE_DIR}

# Build the application using Maven
RUN mvn clean install -U -f pom.xml

# Create a new image to run the application
FROM builder AS runner

# Set the working directory
WORKDIR /app

# Copy the built JAR file from the builder image
COPY --from=builder /app/${SOURCE_CODE_DIR}/target/${JAR_FILE_NAME} .

# Delete the Git directory
RUN rm -rf ${SOURCE_CODE_DIR}

# Run the application
ENTRYPOINT exec java ${JAVA_OPTS} -jar ${JAR_FILE_NAME}
