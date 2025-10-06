# Architecture

This document explains how Kapture wires Git interception, configuration, HTTP lookups, and build tooling together.
Use it as a reference when reasoning about behaviour or planning new features.

## Module Overview

```
root
├── core
│   ├── config            // configuration loading & models
│   ├── exec              // process launching helpers (Exec.passthrough)
│   ├── git               // real Git discovery (RealGitResolver)
│   ├── http              // ExternalClient HTTP wrapper
│   ├── model             // Invocation + shared DTOs
│   └── util              // Environment helpers, logging
├── interceptors
│   ├── GitInterceptor    // pre/post hook contract
│   ├── BranchPolicyInterceptor
│   ├── StatusGateInterceptor
│   └── session           // SessionTrackingInterceptor + persistence
└── cli
    └── Main.kt           // entrypoint, command routing, interceptor loop
```

Each interceptor lives in `interceptors`, depends on the shared primitives in `core`, and is discovered via
`InterceptorRegistry`. The CLI module is the only one that produces runnable artefacts (Shadow JAR and GraalVM native
image).

## Execution Flow

1. `MainKt.main` receives the raw arguments exactly as the user typed them (e.g. `git commit -m "msg"`).
2. `Config.load()` resolves the config file using this precedence:
   1. explicit path passed to `load`
   2. `KAPTURE_CONFIG` environment variable
   3. `${KAPTURE_LOCAL_STATE}/config.json` (defaults to `~/.kapture/state/config.json`)
   If no config exists, defaults are used and the state directory is created.
3. `RealGitResolver.resolve` enumerates candidates from `REAL_GIT`, the config hint, `$PATH`, and well-known fallbacks.
   It avoids returning the wrapper artefact itself to stop infinite recursion.
4. `ExternalClient` is constructed with the base URL/API key from the config. It uses Ktor CIO, content negotiation, and
   JSON parsing to perform ticket lookups and session tracking calls. Timeouts are set to 10 seconds.
5. The CLI branches:
   - If the first argument is `kapture`, the built-in subcommand handler runs (currently `status`/`help`).
   - Certain read-only Git commands (`--version`, `help`, `rev-parse`, completion flags, etc.) are proxied immediately.
   - Otherwise an `Invocation` object is created and passed to each interceptor.
6. `GitInterceptor.before` runs in declaration order. Returning a non-null exit code stops further processing and causes
   the CLI to exit immediately (used for blocking push/commit/bad branch).
7. If no interceptor blocks execution, `Exec.passthrough` spawns the real Git process with the preserved working
   directory, environment, and arguments.
8. After Git exits, `GitInterceptor.after` hooks run in the same order (e.g. to emit session tracking events).
9. The CLI terminates with Git’s exit code.

## Network Integration

`ExternalClient` exposes two operations:
- `getTicketStatus(ticketId)` – GET `/tickets/{id}/status`, returning a simple status string used by `StatusGateInterceptor`.
- `trackSession(snapshot)` – POST `/sessions/track`, guarded by `trackingEnabled`.

Both calls catch and log transient failures through `Environment.debug`, never surfacing credentials, never raising
exceptions to the CLI unless the response indicates a deterministic error (e.g. HTTP 4xx ≠ 404).

## Configuration Surface

Key settings from `Config`:
- `branchPattern` – regex with a named capture `ticket` that extracts the ticket identifier from branch names.
- `enforcement.branchPolicy` / `.statusCheck` – `WARN`, `BLOCK`, or `OFF` mode per interceptor.
- `statusRules` – allowed ticket statuses for commit/push operations.
- `trackingEnabled` – disables session tracking when `false`.
- `sessionTrackingIntervalMs` – minimum interval between session snapshots.
- `realGitHint` – preferred path for the actual Git binary.
- `ticketSystem` – string flag for client-side specialisation (currently informational).

The defaults are serialised with `encodeDefaults = true`, meaning a generated config file contains every key to simplify
bootstrap.

## Native Image Build

The CLI module applies `org.graalvm.buildtools.native` and configures the `main` binary to:
- reuse the shadow fat JAR (`useFatJar = true`)
- automatically detect resource files on the classpath
- pass critical flags (`--install-exit-handlers`, `--report-unsupported-elements-at-runtime`,
  `--enable-url-protocols=https`) to satisfy Ktor/CIO requirements

Gradle exposes helpful tasks:
- `:cli:nativeCompile` – build the release native binary
- `:cli:nativeRun` – execute the native image in place
- `:cli:nativeTest` – run tests against the native runtime (if/when added)

When adding libraries that rely on reflection or dynamic proxies, capture metadata automatically by keeping the
metadata repository enabled (default) and, if necessary, check generated configs under `build/native/metadata`.

## Extending the Pipeline

To add a new interceptor:
1. Implement `GitInterceptor`. Use `before` to gate execution and `after` for post-processing.
2. Register the interceptor inside `InterceptorRegistry.interceptors`, respecting ordering constraints.
3. Add unit tests under `interceptors/src/test/kotlin` and integration tests if the interceptor interacts with Git or HTTP.
4. Document new configuration keys (update `Config` and docs as necessary).

Shared concerns (config loading, HTTP, logging) should live in `core` so they can be reused across interceptors.
