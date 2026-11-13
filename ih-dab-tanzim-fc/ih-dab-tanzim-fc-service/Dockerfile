# OpenShift imaji. Once: mvn clean install -Pservice
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20
ENV SPRING_PROFILES_ACTIVE=openshift \
    JAVA_OPTS_APPEND="-Dfile.encoding=UTF-8"
COPY target/ih-dab-tanzim-fc-service-*.jar /deployments/app.jar
EXPOSE 8090
