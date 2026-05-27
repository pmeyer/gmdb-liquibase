FROM eclipse-temurin:23-jre

ARG JAR_FILE=target/gmdb-liquibase.jar

WORKDIR /app

COPY ${JAR_FILE} /app/gmdb-liquibase.jar

ENV SPRING_PROFILES_ACTIVE=migrate

ENTRYPOINT ["java", "-jar", "/app/gmdb-liquibase.jar"]
