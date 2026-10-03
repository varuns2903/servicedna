FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Runs as an unprivileged user (numeric, so Kubernetes can check runAsNonRoot).
RUN addgroup -S -g 10001 servicedna && adduser -S -D -H -u 10001 -G servicedna servicedna
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
