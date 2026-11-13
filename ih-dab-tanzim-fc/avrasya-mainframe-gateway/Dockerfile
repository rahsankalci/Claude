# Once: mvn clean install -Pservice
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20
ENV GATEWAY_CONNECTOR=zos-connect \
    JAVA_OPTS_APPEND="-Dfile.encoding=UTF-8 -Dgateway.connector=${GATEWAY_CONNECTOR}"
COPY target/avrasya-mainframe-gateway-*.jar /deployments/app.jar
EXPOSE 8091
