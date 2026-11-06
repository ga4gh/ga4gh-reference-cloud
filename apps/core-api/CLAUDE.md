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
* Fix formatting: `./gradlew spotlessApply` (run it before committing; Checkstyle has no auto-fix)
* Formatting belongs to Spotless only; `config/checkstyle/checkstyle.xml` must not add layout rules, or the two tools will disagree.
* Suppress a SpotBugs finding in code with `@SuppressFBWarnings(value = "...", justification = "...")` (always with a justification); project-wide exclusions go in `config/spotbugs/exclude.xml`, each with a reason. Suppress Checkstyle with `@SuppressWarnings("checkstyle:<rule>")` and a comment.
* The Dockerfile runs `gradle bootJar`, so the image build doesn't run static analysis or tests; CI is the gate.

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

* **Database Migration Guard:** Never let Hibernate auto-generate or modify production schemas (`ddl-auto: update`). Always request or generate explicit Liquibase or Flyway migration scripts when entity structures change.
* **Data Connect data models are `json`, not `jsonb`:** `data_connect_table_type.data_model_template` and `data_connect_search.data_model` use `json` so the data model's `properties` (the table's columns) keep their authored order; `jsonb` reorders object keys. Keep any new data model column `json`. Databases seeded while `data_model_template` was `jsonb` need `./scripts/refcloud-setup.sh --reset` to restore the authored order.
* **Validate Bean Validation Constraints:** When writing or modifying API inputs, always ensure validation annotations (e.g., `@Valid`, `@NotBlank`, `@Size`) are placed on Controller endpoints and target DTO records.
* **Idempotency & Transaction Control:** Always explicitly declare `@Transactional(readOnly = true)` at the class level for read services, and override with a mutable `@Transactional` only on write methods.
* **Test Isolation:** When generating tests, use `@WebMvcTest` for slice-testing controllers and `@DataJpaTest` for repositories. Avoid defaulting to a heavy, slow `@SpringBootTest` unless a full integration test is explicitly requested.
* **Review Generated Dependencies:** Before adding new third-party libraries to `pom.xml` or `build.gradle`, stop and check if Spring Boot Starter dependencies already provide a managed, version-compatible solution.
