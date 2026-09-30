FROM gradle:8.12.1-jdk21-alpine AS build
WORKDIR /workspace
COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts ./
COPY --chown=gradle:gradle src ./src
RUN gradle --no-daemon clean bootJar -x test

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S skinproof && adduser -S skinproof -G skinproof
COPY --from=build /workspace/build/libs/skinproof-api.jar app.jar
USER skinproof
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]