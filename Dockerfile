FROM amazoncorretto:17

WORKDIR /app/prebid-server

VOLUME /app/prebid-server/conf
VOLUME /app/prebid-server/data

COPY src/main/docker/run.sh ./
COPY src/main/docker/application.yaml ./
COPY target/prebid-server.jar ./
COPY sample/prebid-config-docker.yaml ./
COPY sample/stored ./stored
COPY sample/sample-app-settings.yaml ./


EXPOSE 8080
EXPOSE 8060

ENTRYPOINT [ "/app/prebid-server/run.sh" ]
