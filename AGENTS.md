# Repository Guidelines

## Project Structure & Module Organization
Kapture is a Gradle-based Kotlin CLI divided into logical modules: place shared models, config loading, and git resolution in `src/core/main/kotlin`, interceptor logic (branch policy, status gates, session tracking) in `src/interceptors/main/kotlin`, and the entrypoint plus command wiring in `src/cli/main/kotlin`. Mirror this layout for tests under `src/<module>/test/kotlin`. Use `src/<module>/main/resources` for bundled defaults (e.g., `config.json`). Build artefacts and the runnable Shadow JAR live under `build/`.

## Build, Test, and Development Commands
- `./gradlew build` – compiles all modules and assembles the JVM artefacts.
- `./gradlew test` – executes unit and integration tests; keep it green before pushing.
- `./gradlew shadowJar` – produces the distributable `build/libs/kapture-all.jar`.
- `KAPTURE_DEBUG=1 ./gradlew run --args="status"` – runs the CLI with verbose interceptor logs for local debugging.

## Coding Style & Naming Conventions
Follow the Kotlin official style: four-space indentation, trailing commas where legal, and expression-bodied functions only when they improve clarity. Use `UpperCamelCase` for classes, `lowerCamelCase` for functions and properties, and `UPPER_SNAKE_CASE` for constants. Keep interceptor names explicit (`BranchPolicyInterceptor`, `SessionTracker`). Add top-level KDoc for public APIs, especially config classes and CLI entrypoints. Prefer immutable data classes and dependency injection via constructor parameters.

## Testing Guidelines
Write Kotlin test suites with JUnit 5 (`kotlin("test")`). Cover regex ticket extraction, config precedence, interceptor WARN/BLOCK/OFF behaviour, and session timeout transitions. For integration tests, spin up temporary Git repositories and a mock HTTP server to validate exit codes and Jira interactions. Name tests using backticked strings that describe behaviour (`"blocks push when status disallowed"`). Run `./gradlew test` before every PR and ensure new features include regression coverage.

## Commit & Pull Request Guidelines
Commits should be short, imperative sentences (`Add session tracker persistence`). Group related changes and avoid mixing refactors with features. PRs need: a concise summary, linked issue or Jira ticket, testing notes (`./gradlew test`), and screenshots or logs when touching user-facing output. Call out configuration or migration steps explicitly. Ask for focused reviews on security-sensitive paths (ticket validation, session file handling).

## Security & Configuration Tips
Respect user environments: always forward `GIT_*`, `PAGER`, `LESS`, `EDITOR`, `VISUAL`, `SSH_ASKPASS`, and `GPG_TTY`. Never log credentials or full command strings; redact API keys in debug output. Honour `trackingEnabled = false` to skip time tracking writes, and store session data only under `${KAPTURE_LOCAL_STATE}/state`. Validate `REAL_GIT` resolution to avoid recursion when the wrapper is symlinked as `git`.
