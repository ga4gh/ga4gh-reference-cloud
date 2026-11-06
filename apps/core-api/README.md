# GA4GH Reference Cloud: Core API

Core backend API for the GA4GH Reference Cloud.

**For instructions on how to run the full reference cloud, please see the main [README](../../README.md).**

To run the core API by itself, see below.

**Note:** you MUST install all [required prerequisites](../../README.md#getting-started) AND run the [setup script](../../scripts/refcloud-setup.sh) first to create required resources.

## Run

* Run the core API server: `./gradlew bootRun`

## Static Analysis

Static analysis runs in CI on every pull request that changes `apps/core-api/`, and weekly. The Gradle-based tools are pinned in `build.gradle`, so local runs match CI. Run them all with `./gradlew staticAnalysis`, or a single check:

| Check | Command |
|-|-|
| Compilation (javac, all warnings as errors) | `./gradlew compileJava compileTestJava` |
| Formatting ([Spotless](https://github.com/diffplug/spotless) with [palantir-java-format](https://github.com/palantir/palantir-java-format)) | `./gradlew spotlessCheck` |
| Linting ([Checkstyle](https://checkstyle.org/), `config/checkstyle/checkstyle.xml`) | `./gradlew checkstyleMain` |
| Bugs and security rules ([SpotBugs](https://spotbugs.github.io/) with [Find Security Bugs](https://find-sec-bugs.github.io/)) | `./gradlew spotbugsMain` |
| Dependency vulnerabilities in `gradle.lockfile` ([Trivy](https://trivy.dev/), HIGH and CRITICAL; needs only Docker) | `./scripts/audit-deps.sh` |

To fix formatting: `./gradlew spotlessApply`

Every dependency version is locked in `gradle.lockfile` (strict mode: the build fails if it is missing or out of date). After adding, removing or upgrading a dependency, refresh it with `./gradlew dependencies --write-locks` and commit it. To fix a vulnerable dependency, upgrade it (for libraries managed by Spring Boot, usually the Spring Boot plugin version) and refresh the lock. A vulnerability with no usable fix can be accepted in `.trivyignore`, with a reason.

Reports are written to `build/reports/` (`checkstyle/main.html`, `spotbugs/main.html`).

## Configuration

See [`src/main/resources/application.yml`](./src/main/resources/application.yml) for a list of configurable properties. Rather than making changes to the YAML file directly, we recommend setting environment variables that will override the defaults in `application.yml`. For example, the value:

```
ga4gh:
  refcloud:
    drs:
      service-info:
        id: org.ga4gh.refcloud.drs.local
```

can be overriden by setting the environment variable:

```
GA4GH_REFCLOUD_DRS_SERVICE_INFO_ID="{new_value}"
```

For more information, see the following article on [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html).