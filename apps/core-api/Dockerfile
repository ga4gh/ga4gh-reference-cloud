FROM gradle:9.5.1-jdk25-corretto AS builder

WORKDIR /home/gradle/project

COPY --chown=gradle:gradle build.gradle* settings.gradle* ./
COPY --chown=gradle:gradle src ./src

RUN gradle build -x test --no-daemon

FROM amazoncorretto:25.0.3-alpine

RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

COPY --from=builder /home/gradle/project/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
