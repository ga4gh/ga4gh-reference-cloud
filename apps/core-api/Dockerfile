# built from the repo root, so the image can include compute/workflows/: e.g.
#   docker build -f apps/core-api/Dockerfile .
# /.dockerignore lists what the build can see
FROM gradle:9.5.1-jdk25-corretto AS builder

WORKDIR /home/gradle/project

# gradle.lockfile pins every dependency version (strict locking: the build fails without it)
COPY --chown=gradle:gradle apps/core-api/build.gradle* apps/core-api/settings.gradle* apps/core-api/gradle.lockfile ./
COPY --chown=gradle:gradle apps/core-api/src ./src

# bootJar builds only the runnable jar: static analysis and tests (both part of `gradle build`) are CI's job
RUN gradle bootJar --no-daemon

FROM amazoncorretto:25.0.3-alpine

# git: WES fetches allowlisted git and TRS workflows at their pinned commits (RunPreparationService)
RUN apk add --no-cache git

# fixed numeric ids, so kubernetes can verify the user is non-root (the helm chart sets runAsUser to match)
RUN addgroup -S -g 10001 appgroup && adduser -S -u 10001 -G appgroup appuser

WORKDIR /app

COPY --from=builder /home/gradle/project/build/libs/*.jar app.jar

RUN chown -R appuser:appgroup /app

# the bundled workflows (compute/workflows/nextflow/<name>), which WES copies into each run that uses one. owned by
# root, so the app can read them but not change them
COPY compute/workflows /app/compute/workflows
ENV GA4GH_REFCLOUD_COMPUTE_WES_BUNDLEDWORKFLOWSBASEDIR=/app

USER 10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
