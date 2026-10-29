# Project Guide: GA4GH Reference Cloud API

## Project Overview & Architecture

The GA4GH Reference Cloud (Refcloud) is a reference implementation of multiple API specifications from the [Global Alliance for Genomics and Health](https://ga4gh.org). The Refcloud is designed to provide a live model of GA4GH standards in action, demonstrating how they work as running applications, and elucidating how various models and concepts can be connected across different GA4GH Work Streams.

This repo serves as the core application's backend layer. There are separate repos for the frontend, CLI, helm chart, etc.

* **Tech Stack:** Java Spring Boot, PostgreSQL, Liquibase

### Directory Layout
* `src/main` - Main Java/Spring Boot application code
* `src/test/` - Unit and integration tests
* `liquibase/` - Contains config and commands to apply database migrations via liquibase
* `contrib/` - Config files used by dependency services spun up via docker compose
* `setup/` - One-time environment setup scripts, grouped by target (e.g. `setup/postgres/` for admin database setup, such as role creation, run before the Liquibase changelog)

---

## 🛠️ Core Commands

### Development & Build
* Start dependency services: `docker-compose up -d`
* Start local dev server: `./gradlew bootRun`

### Code Quality & Validation
* TBD: Run linter
* TBD: Fix linting/formatting errors
* TBD: Run type check

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
* **Validate Bean Validation Constraints:** When writing or modifying API inputs, always ensure validation annotations (e.g., `@Valid`, `@NotBlank`, `@Size`) are placed on Controller endpoints and target DTO records.
* **Idempotency & Transaction Control:** Always explicitly declare `@Transactional(readOnly = true)` at the class level for read services, and override with a mutable `@Transactional` only on write methods.
* **Test Isolation:** When generating tests, use `@WebMvcTest` for slice-testing controllers and `@DataJpaTest` for repositories. Avoid defaulting to a heavy, slow `@SpringBootTest` unless a full integration test is explicitly requested.
* **Review Generated Dependencies:** Before adding new third-party libraries to `pom.xml` or `build.gradle`, stop and check if Spring Boot Starter dependencies already provide a managed, version-compatible solution.
