FROM eclipse-temurin:21-jre-ubi9-minimal
WORKDIR /opt/vripper-web
ENTRYPOINT ["java", "-jar", "vripper-web.jar"]
ADD vripper-web.jar /opt/vripper-web/vripper-web.jar
