FROM eclipse-temurin:17-jdk-alpine
WORKDIR /app
COPY target/sync-bridge-1.0.0.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]