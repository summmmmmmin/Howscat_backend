# GitHub Actions에서 빌드된 JAR을 받아서 실행만 함
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
