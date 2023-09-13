FROM maven:3.8.3-amazoncorretto-17 AS mvnbuild

ENV BUILD_DIR /app/prebid-cache

RUN mkdir -p $BUILD_DIR
WORKDIR $BUILD_DIR

# Copying pom for dependencies
COPY ./pom.xml ./pom.xml
# Fetching dependencies as to cache this step
RUN ["/usr/local/bin/mvn-entrypoint.sh", "mvn", "verify", "clean", "--fail-never"]

# Bundling source code
COPY ./ ./

# Taking default configs and making them local. This is temporary. Will replace with proper
# env config injection when finalizing pipeline.
RUN mv ./internal/application.default.yml ./src/main/resources/application-local.yml
 
# Packaging JAR
RUN mvn clean package

## Restarting with corretto 17 for a lighter image.
FROM amazoncorretto:17

WORKDIR /app/prebid-server

VOLUME /app/prebid-server/conf
VOLUME /app/prebid-server/data

COPY src/main/docker/run.sh ./
COPY src/main/docker/application.yaml ./
COPY target/prebid-server.jar ./

EXPOSE 8080
EXPOSE 8060

ENTRYPOINT [ "/app/prebid-server/run.sh" ]
