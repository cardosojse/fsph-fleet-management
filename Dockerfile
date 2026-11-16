FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home app
COPY --from=build --chown=10001:10001 /app/target/*.jar ./app.jar
USER 10001:10001
ENV PORT=10000
EXPOSE 10000
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
