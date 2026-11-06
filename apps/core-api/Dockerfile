FROM gradle:9.5.1-jdk25-corretto AS builder

WORKDIR /home/gradle/project

# gradle.lockfile pins every dependency version (strict locking: the build fails without it)
COPY --chown=gradle:gradle build.gradle* settings.gradle* gradle.lockfile ./
COPY --chown=gradle:gradle src ./src

# bootJar builds only the runnable jar: static analysis and tests (both part of `gradle build`) are CI's job
RUN gradle bootJar --no-daemon

FROM amazoncorretto:25.0.3-alpine

# fixed numeric ids, so kubernetes can verify the user is non-root (the helm chart sets runAsUser to match)
RUN addgroup -S -g 10001 appgroup && adduser -S -u 10001 -G appgroup appuser

WORKDIR /app

COPY --from=builder /home/gradle/project/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
