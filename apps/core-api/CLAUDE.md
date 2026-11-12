# Project Guide: GA4GH Reference Cloud Core API

## Project Overview & Architecture

This repo serves as the refcloud's core backend layer.

* **Tech Stack:** Java Spring Boot, PostgreSQL, Liquibase

### Directory Layout
* `src/main` - Main Java/Spring Boot application code
* `src/test/` - Unit and integration tests
* `liquibase/` - Contains config and commands to apply database migrations via liquibase

---

## Core Commands

### Development & Build

* Start local dev server: `./gradlew bootRun`

### Code Quality & Validation

All tools are pinned in `build.gradle`, so local runs match CI (`.github/workflows/static-analysis-core-api.yml`).

* Run all static analysis: `./gradlew staticAnalysis`
* Compile (javac `-Xlint:all -Werror`, `processing` excluded for Lombok): `./gradlew compileJava compileTestJava`
* Check formatting (Spotless + palantir-java-format, 4-space indent, 120 columns): `./gradlew spotlessCheck`
* Run linter (Checkstyle, non-formatting rules only): `./gradlew checkstyleMain`
* Find bugs and security issues (SpotBugs + Find Security Bugs, medium confidence and above): `./gradlew spotbugsMain`
* Audit `gradle.lockfile` (every configuration, runtime through build tooling) for HIGH/CRITICAL CVEs (Trivy, pinned Docker image; needs only Docker; not part of `staticAnalysis`): `./scripts/audit-deps.sh`. Fix findings by upgrading (Spring-managed libraries: bump the Spring Boot plugin) and refreshing the lock; only add an ID to `.trivyignore` when no usable fix exists, with a reason comment.
* Dependency locking is strict: after any dependency change, run `./gradlew dependencies --write-locks` and commit `gradle.lockfile`, or the build fails. Never hand-edit the lockfile.
* Fix formatting: `./gradlew spotlessApply` (run it before committing; Checkstyle has no auto-fix)
* Formatting belongs to Spotless only; `config/checkstyle/checkstyle.xml` must not add layout rules, or the two tools will disagree.
* Suppress a SpotBugs finding in code with `@SuppressFBWarnings(value = "...", justification = "...")` (always with a justification); project-wide exclusions go in `config/spotbugs/exclude.xml`, each with a reason. Suppress Checkstyle with `@SuppressWarnings("checkstyle:<rule>")` and a comment.
* The Dockerfile runs `gradle bootJar` (with `gradle.lockfile` copied in, so the image uses the locked versions), so the image build doesn't run static analysis or tests; CI is the gate. The dependency audit also runs weekly, so a new advisory can fail CI without code changes.

### Testing

* TBD: Run all tests
* TBD: Run specific test file

---

## 🎨 Code Style & Preferences

### Architecture & Framework Patterns
* **Dependency Injection:** Use constructor injection as much as possible. Never use `@Autowired` on fields unless absolutely necessary. Mark dependencies as `final`.
* **Data Flow Architecture:** Strict tier separation: `Controller/Resource` -> `Service` -> `Repository`. Controllers must never access repositories directly.
* **DTO Mapping:** Use explicit mappers (e.g., MapStruct or manual static factory methods). Do not leak `@Entity` objects to the Controller/API layer.
* **Asynchronous Execution:** Use `@Async` only on void or `CompletableFuture` returning service methods; always specify a custom named executor pooling bean.

### Java & Spring Idioms
* **Modern Java:** Prefer Java 17/21 features: use `record` classes for DTOs/Value Objects, block strings for multiline SQL, and switch pattern matching.
* **Null Safety:** Avoid returning `null`. Return `Optional<T>` for service lookups that may fail to find a record.
* **Lombok Usage:** Use `@RequiredArgsConstructor` for dependency injection. Avoid `@Data` on `@Entity` classes to prevent infinite recursion in `toString()`/`hashCode()` via JPA relationships; use `@Getter` and `@Setter` explicitly instead.
* **Exception Handling:** Use a centralized `@ControllerAdvice` / `@RestControllerAdvice` handler with `@ExceptionHandler` methods. Do not catch-and-swallow exceptions in services.

---

## ⚠️ Behavioral Guardrails & Rules

* **Database Migration Guard:** Never let Hibernate auto-generate or modify production schemas (`ddl-auto: update`). Always request or generate explicit Liquibase or Flyway migration scripts when entity structures change. Entity field types must match their columns (e.g. `Long` ↔ `BIGINT`, `Instant` ↔ `TIMESTAMP WITH TIME ZONE`). Check with a one-off run against a fully migrated database: `SPRING_JPA_HIBERNATE_DDL_AUTO=validate ./gradlew bootRun`. Startup fails on the first mismatch, so fix it and re-run until it starts. The whole schema validates as of compute Task 3.
* **Data Connect data models are `json`, not `jsonb`:** `data_connect_table_type.data_model_template` and `data_connect_search.data_model` use `json` so the data model's `properties` (the table's columns) keep their authored order; `jsonb` reorders object keys. Keep any new data model column `json`. Databases seeded while `data_model_template` was `jsonb` need `./scripts/refcloud-setup.sh --reset` to restore the authored order.
* **Compute connections come only from the connections file:** `ComputeConfig` (`ga4gh.refcloud.compute`) binds from the file imported via `spring.config.import` (`REFCLOUD_COMPUTE_CONNECTIONS_FILE`, default `deploy/local/compute/connections.yml`), which the admin CLI also reads. Don't add connection settings to `application.yml` or override them with env vars, or the two apps disagree. Resolve relative paths with `ComputeConnections`, never against the working directory.
* **Postgres `CHECK` constraints pass on `NULL`:** a regex or comparison on a nullable column evaluates to `NULL` (which passes) when the column is null. Use explicit `IS NOT NULL` / `num_nulls(...) = 0`, or wrap in `COALESCE(..., FALSE)`.
* **Timestamps are `timestamptz` and UTC:** every timestamp column is `TIMESTAMP WITH TIME ZONE`, mapped to `java.time.Instant`, and serialized as RFC 3339 UTC (e.g. `2026-11-11T20:24:16Z`). New timestamp columns follow the same rule; never use `TIMESTAMP WITHOUT TIME ZONE` or `LocalDateTime` for stored or API times.
* **Never register an `AuthenticationProvider` as a Spring bean:** Spring Security adds a lone `AuthenticationProvider` bean to the *global* `AuthenticationManager`, which every filter chain falls back to when its own provider fails, so its tokens would authenticate on every path. Construct providers inside the one `SecurityFilterChain` that needs them (as `SecurityConfig` does for run-scoped TES tokens, `rcrun_…`), wrapped in a `ProviderManager` with no parent.
* **Validate Bean Validation Constraints:** When writing or modifying API inputs, always ensure validation annotations (e.g., `@Valid`, `@NotBlank`, `@Size`) are placed on Controller endpoints and target DTO records.
* **Idempotency & Transaction Control:** Always explicitly declare `@Transactional(readOnly = true)` at the class level for read services, and override with a mutable `@Transactional` only on write methods.
* **Test Isolation:** When generating tests, use `@WebMvcTest` for slice-testing controllers and `@DataJpaTest` for repositories. Avoid defaulting to a heavy, slow `@SpringBootTest` unless a full integration test is explicitly requested.
* **Review Generated Dependencies:** Before adding new third-party libraries to `pom.xml` or `build.gradle`, stop and check if Spring Boot Starter dependencies already provide a managed, version-compatible solution.
