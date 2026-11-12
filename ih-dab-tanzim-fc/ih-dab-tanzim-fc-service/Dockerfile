# OpenShift imaji. Once: mvn clean install -Pservice
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:1.18
ENV SPRING_PROFILES_ACTIVE=openshift \
    JAVA_OPTS_APPEND="-Dfile.encoding=UTF-8"
COPY target/ih-dab-tanzim-fc-service-*.jar /deployments/app.jar
EXPOSE 8090
