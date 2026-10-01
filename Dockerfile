FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY build/libs/jobtrace-0.1.0-SNAPSHOT.jar /app/jobtrace.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/jobtrace.jar"]
