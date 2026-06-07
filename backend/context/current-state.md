# Backend current state

## Purpose

- `notes` is the Spring Boot API root for the Notes product.
- The backend is an independent Gradle root at `backend/`.

## Identifiers

- Kotlin package: `dev.vvanttinen.notes`.
- Gradle group: `dev.vvanttinen`.
- Gradle project version: `0.0.1-SNAPSHOT`.
- Spring application name: `notes`.

## Toolchain and build

- Gradle settings file: `settings.gradle.kts`.
- Gradle root project name: `notes`.
- Gradle wrapper version: `9.5.1`.
- Java toolchain version: `21`.
- Kotlin JVM plugin version: `2.2.21`.
- Kotlin Spring plugin version: `2.2.21`.
- Kotlin JPA plugin version: `2.2.21`.
- Spring Boot Gradle plugin version: `4.0.6`.
- Spring dependency management plugin version: `1.1.7`.
- Kotlin compiler options include `-Xjsr305=strict` and `-Xannotation-default-target=param-property`.
- JUnit Platform is configured for Gradle `Test` tasks.

## Implemented baseline

- `NotesApplication.kt` contains the generated `@SpringBootApplication` entry point.
- No backend controllers, services, repositories, or entities are currently present under the main package.
- No explicit datasource configuration is currently present in `application.properties`.
- No Entra-specific tenant, issuer, client, or redirect values are currently configured.
- `src/main/resources/db/migration/` exists, but no Flyway migration files are currently present.

## Important dependencies and configuration

- Declared application dependencies include Spring Web MVC, Validation, Spring Data JPA, Flyway, Flyway PostgreSQL database support, OAuth2 Resource Server, Actuator, PostgreSQL JDBC driver, Docker Compose support, Kotlin reflection, and Jackson Kotlin support.
- Declared test dependencies include Spring Boot starter test modules, Spring Boot Testcontainers support, Testcontainers, Testcontainers PostgreSQL, Kotlin JUnit 5 support, and the JUnit Platform launcher.
- Local PostgreSQL Compose configuration exists in `compose.yaml`.
- Compose service: `postgres`.
- Compose image: `postgres:latest`.
- Compose port mapping exposes container port `5432` without a fixed host port.
- Compose database credentials are present but redacted.

## Verification

- Primary command on Windows: `gradlew.bat test` from `backend/`.
- Current test classes are `NotesApplicationTests`, `TestcontainersConfiguration`, and `TestNotesApplication`.
- The current application context test imports a PostgreSQL Testcontainers configuration.

## Known gaps

- No API endpoints, persistence model, repositories, services, or Flyway migrations are implemented yet.
- Datasource and OAuth2 Resource Server settings are not configured beyond declared dependencies.
