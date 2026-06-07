# Frontend current state

## Purpose

- `Notes` is the Android application root for the Notes product.
- The frontend is an independent Gradle root at `frontend/`.

## Identifiers

- Product name: `Notes`.
- Android namespace: `dev.vvanttinen.notes`.
- Android application ID: `dev.vvanttinen.notes`.
- Kotlin package: `dev.vvanttinen.notes`.

## Toolchain and build

- Gradle settings file: `settings.gradle.kts`.
- Gradle root project name: `Notes`.
- Gradle wrapper version: `9.4.1`.
- Android Gradle Plugin version: `9.2.1`.
- Kotlin Compose plugin version: `2.2.10`.
- Gradle toolchain resolver plugin version: `1.0.0`.
- Java source compatibility: `11`.
- Java target compatibility: `11`.
- Generated Gradle daemon JVM properties are present and specify `toolchainVersion=21`; no machine-specific JDK path is recorded.
- Minimum SDK: `26`.
- Target SDK: `36`.
- Compile SDK: Android `36.1`.
- Compose is enabled through `buildFeatures.compose = true`.

## Implemented baseline

- The app contains a generated `MainActivity` that enables edge-to-edge rendering and shows a Compose `Greeting("Android")` inside `NotesTheme`.
- Theme files exist under `dev.vvanttinen.notes.ui.theme`.
- Launcher icon and generated XML resource baseline files are present.

## Important dependencies and configuration

- Declared application dependencies include AndroidX Core KTX, Lifecycle Runtime KTX, Activity Compose, Compose UI, Compose Graphics, Compose Tooling Preview, Compose Material 3, and the Compose BOM.
- Declared test dependencies include JUnit 4, AndroidX JUnit, Espresso Core, and Compose UI test JUnit 4.
- Room is not configured.
- WorkManager is not configured.
- Networking libraries are not configured.
- Dependency injection is not configured.
- MSAL is not configured.
- `local.properties` exists as `<present but not inspected>`.

## Verification

- Primary command on Windows: `gradlew.bat testDebugUnitTest` from `frontend/`.
- Current unit test: generated `ExampleUnitTest` asserting `2 + 2 == 4`.
- Current instrumented test: generated `ExampleInstrumentedTest` asserting the application package name.

## Known gaps

- No product-specific screens, local storage, sync, authentication, networking, dependency injection, or background work are implemented yet.
