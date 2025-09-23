FROM eclipse-temurin:25-jre
WORKDIR /opt/vripper-web
ENTRYPOINT ["java", "-jar", "vripper-web.jar"]
ADD vripper-web.jar /opt/vripper-web/vripper-web.jar
