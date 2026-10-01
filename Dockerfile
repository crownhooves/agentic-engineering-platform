FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src
COPY reference ./reference

RUN mvn clean package -DskipTests


FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /build/target/*.jar /app/agentic.jar
COPY --from=build /build/reference /app/reference

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "/app/agentic.jar"]