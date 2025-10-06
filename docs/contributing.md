# Contributing & Extending Kapture

This guide explains how to work on the codebase, add new functionality, and contribute it back safely.

## Environment Setup

- Install a JDK 21 distribution. For native builds, use GraalVM 21 and run `gu install native-image`.
- Clone the repository and rely on the Gradle wrapper (`./gradlew`) – no global Gradle install required.
- Optional tooling: IntelliJ IDEA with the Kotlin plugin recognises the multi-module project structure.

## Core Workflows

### Build & Test

```bash
./gradlew build        # compiles all modules and runs tests
./gradlew test         # unit + integration test suites
./gradlew shadowJar    # produces cli/build/libs/kapture.jar
./gradlew :cli:nativeCompile   # builds the native binary (requires GraalVM)
```

Set `KAPTURE_DEBUG=1` during `run` or tests to inspect interceptor logging.

### Coding Standards

- Follow the Kotlin official style (4 spaces, trailing commas, descriptive names).
- Place shared code in `core`, interceptor logic in `interceptors`, and CLI wiring in `cli`.
- Add KDoc to new public APIs, especially configuration or entrypoints.
- Keep commits concise and imperative (see `AGENTS.md` for the full guidelines).

### Testing Expectations

- Use JUnit 5 (`kotlin("test")`).
- Cover:
  - Branch regex extraction edge cases
  - Config precedence and overrides
  - Interceptor behaviour in WARN/BLOCK/OFF modes
  - Session tracking intervals and `trackingEnabled` short-circuiting
- Integration tests should spin up temporary Git repositories and mock HTTP servers when the behaviour depends on
  external state.

## Adding Features

### New Interceptors

1. Create a class implementing `GitInterceptor`.
2. Register it in `InterceptorRegistry.interceptors` in the intended order.
3. Document any configuration flags you introduce (update `Config` and docs).
4. Provide tests covering both success (allow) and failure (block/warn) paths.

### HTTP Integrations

- Extend `ExternalClient` or subclass it if you need custom behaviour.
- Avoid leaking secrets into logs. Use `Environment.debug` for diagnostic output; it automatically respects
  `KAPTURE_DEBUG`.
- Consider adding feature flags to the config so functionality can be toggled without code changes.

### Native Image Compatibility

- New dependencies relying on reflection may need manual configuration. Keep the metadata repository enabled and inspect
  `cli/build/native/metadata` after a native build.
- If you add dynamic proxies, register them through the GraalVM plugin DSL.

## Contribution Checklist

- [ ] Lint by running `./gradlew ktlintFormat` or similar if you add a linter (not currently enforced).
- [ ] Run `./gradlew test` locally; include evidence in your PR description.
- [ ] Update documentation (`README.md`, `docs/`) for any user-facing change.
- [ ] Mention migration/configuration steps explicitly in PR notes.

Following these practices keeps the wrapper predictable and safe for everyone who depends on it.
