# Kapture - JVM Kotlin Specification

## Goal

Build a JVM-based Git CLI wrapper that:

- Proxies all Git commands with identical UX (autocomplete, interactive flows)
- Intercepts specific verbs for branch policy, ticket validation, and **session-based time tracking**
- Initially supports **Jira** ticket integration; future extensibility for **GitHub Issues**, **Trello**, and other
  systems is planned
- Maintains zero-copy passthrough for completion/help commands
- Single JAR distribution with minimal runtime overhead
- Optionally build a GraalVM native binary for ultra-fast startup on Linux/macOS

---

## Architecture

### Modules

- **core**: Models, config, HTTP client, process execution
- **interceptors**: Branch policy, status gates, session time tracking
- **cli**: Main entry point, command registry

### Execution Model

- **Passthrough mode** (default): Inherit stdin/stdout/stderr via `ProcessBuilder.inheritIO()`
- **Capture mode** (rare): Pipe output only for internal queries (e.g., `git rev-parse`)

---

## Process Execution

```kotlin
object Exec {
    /** Inherit IO - preserves TTY, completion, interactive prompts */
    fun passthrough(
        cmd: List<String>,
        workDir: File? = null,
        env: Map<String, String> = emptyMap()
    ): Int

    /** Capture output - only for internal helper commands */
    fun capture(
        cmd: List<String>,
        workDir: File? = null,
        env: Map<String, String> = emptyMap()
    ): ExecResult
}

data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)
```

**Implementation**: Use `ProcessBuilder` with `.inheritIO()` for passthrough, `.redirectOutput()` for capture.

---

## Autocomplete Preservation

**Never intercept these commands** (bypass directly to real Git):

- `--list-cmds=*`
- `help`, `--version`, `--exec-path`
- `config`, `rev-parse`, `for-each-ref`

**Environment preservation**: Pass through all `GIT_*`, `PAGER`, `LESS`, `EDITOR`, `VISUAL`, `SSH_ASKPASS`, `GPG_TTY`

---

## Real Git Resolution

Priority order:

1. `REAL_GIT` environment variable
2. `realGitHint` from config
3. `which git` (via `ProcessBuilder`)
4. Common paths: `/usr/bin/git`, `/opt/homebrew/bin/git`

**Recursion prevention**: Compare resolved path to current JAR location; skip if wrapper is symlinked as `git`.

---

## Configuration

**Location priority**:

1. `KAPTURE_CONFIG` env → absolute path
2. `${config.localStateRoot}/config.json`
3. Embedded defaults

```kotlin
@Serializable
data class Config(
    val externalBaseUrl: String = "http://localhost:8080",
    val apiKey: String? = null,
    val branchPattern: String = "^(?<ticket>[A-Z]+-\\d+)/[a-z0-9._-]+$",
    val enforcement: Enforcement = Enforcement(),
    val statusRules: StatusRules = StatusRules(),
    val trackingEnabled: Boolean = true,
    val realGitHint: String? = null,
    val sessionTrackingIntervalMs: Long = 300_000,  // 5 minutes default
    val ticketSystem: String = "jira", // reserved for future support: github, trello, etc.
    val localStateRoot: String = System.getenv("KAPTURE_LOCAL_STATE") ?: "${
        System.getProperty(
            "user.home"
        )
    }/.kapture/state"
)

@Serializable
data class Enforcement(
    val branchPolicy: Mode = Mode.WARN,
    val statusCheck: Mode = Mode.WARN
) {
    enum class Mode { WARN, BLOCK, OFF }
}

@Serializable
data class StatusRules(
    val allowCommitWhen: Set<String> = setOf("IN_PROGRESS", "READY"),
    val allowPushWhen: Set<String> = setOf("READY", "IN_REVIEW")
)
```

---

## CLI Entry Point

```kotlin
fun main(args: Array<String>) {
    val config = Config.load()
    val realGit = resolveRealGit(config.realGitHint)
    val client = ExternalClient(config.externalBaseUrl, config.apiKey)

    // Custom commands: `git  <subcommand>`
    if (args.firstOrNull() == "kapture") {
        runKaptureCommand(args.drop(1), config, client, realGit)
        return
    }

    // Bypass completion/help commands
    if (isCompletionOrHelp(args)) {
        exitProcess(Exec.passthrough(listOf(realGit) + args))
    }

    val invocation = Invocation(args, realGit)

    // Before hooks
    Registry.interceptors.forEach { interceptor ->
        interceptor.before(invocation, config, client)?.let { exitCode ->
            exitProcess(exitCode)
        }
    }

    // Execute real Git
    val exitCode = Exec.passthrough(listOf(realGit) + args)

    // After hooks
    Registry.interceptors.forEach { interceptor ->
        interceptor.after(invocation, exitCode, config, client)
    }

    exitProcess(exitCode)
}

fun isCompletionOrHelp(args: Array<String>): Boolean {
    val first = args.firstOrNull()?.lowercase()
    return args.any { it.contains("--list-cmds") } ||
           first in setOf("help", "--version", "--exec-path", "config", "for-each-ref")
}
```

---

## Interceptors

### 1. Branch Policy

**Triggers**: `checkout -b|-B`, `switch -c|--create`, `branch -c|-C`

**Logic**:

1. Extract new branch name from args
2. Validate against `branchPattern` regex
3. Extract ticket ID from branch name
4. Query `GET /tickets/{id}/status` (currently via Jira; abstracted for future systems)
5. If ticket missing:

- `WARN`: Print to stderr, continue
- `BLOCK`: Exit code 2

### 2. Status Gate

**Triggers**: `commit`, `push`

**Logic**:

1. Capture current branch: `git rev-parse --abbrev-ref HEAD`
2. Extract ticket ID from branch name
3. Query `GET /tickets/{id}/status` (currently via Jira; abstracted for future systems)
4. Compare status against `statusRules.allowCommitWhen` or `allowPushWhen`
5. If invalid:

- `WARN`: Print warning, continue
- `BLOCK`: Exit code 3 (commit) or 4 (push)

### 3. Session Time Tracking

**Purpose**: Track developer time spent on branches/tickets for effort estimation

**Triggers**: All Git commands (records session activity)

**Session Model**:

```kotlin
@Serializable
data class TimeSession(
    val branch: String,
    val ticket: String?,
    val startTime: Instant,
    val lastActivityTime: Instant
)
```

**Logic**:

**On ANY Git command execution** (in `after()` hook):

1. Get current branch: `git rev-parse --abbrev-ref HEAD`
2. Extract ticket ID from branch name (if pattern matches)
3. Load active session from local state file (`${config.localStateRoot}/session.json`)
4. **Session continuity check**:

- If no active session OR branch changed → **Start new session**
- If same branch AND `(now - lastActivityTime) < sessionTrackingIntervalMs` → **Update session**
- If same branch AND `(now - lastActivityTime) >= sessionTrackingIntervalMs` → **Close old, start new**

5. **On session close** (branch switch or timeout):

- Calculate total duration: `closedAt - startTime`
- POST `/sessions/track`:

   ```json
   {
  "branch": "PROJ-123/feature",
  "ticket": "PROJ-123",
  "startTime": "2025-10-05T10:00:00Z",
  "endTime": "2025-10-05T11:30:00Z",
  "durationMs": 5400000
}
   ```

6. **Update local session state** with current timestamp as `lastActivityTime`

**State persistence**:

- Local file: `${config.localStateRoot}/session.json`
- Contains only current active session
- Atomic writes to prevent corruption
- All local data is stored under `${KAPTURE_LOCAL_STATE}` (default `$HOME/.kapture/state`)

**Fire-and-forget**: HTTP POST failures are logged but never block Git operations.

**Session timeout**: If no Git activity for `sessionTrackingIntervalMs` (default 5 minutes), the next command closes the
previous session and starts a new one.

---

## HTTP Client

**Library**: Ktor JVM client with CIO engine + Content Negotiation (JSON)

```kotlin
val client = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
    install(HttpTimeout) {
        requestTimeoutMillis = 10_000
        connectTimeoutMillis = 10_000
    }
}

// Add Authorization header if apiKey present
if (apiKey != null) {
    client.config {
        defaultRequest {
            header("Authorization", "Bearer $apiKey")
        }
    }
}
```

**Error handling**: Treat non-2xx as "ticket not found". Never block passthrough unless policy is `BLOCK`.

---

## Exit Codes

- **0**: Success
- **2**: Branch policy violation (BLOCK mode)
- **3**: Status gate violation on commit (BLOCK mode)
- **4**: Status gate violation on push (BLOCK mode)
- **127**: Wrapper error (cannot find real Git)
- **Passthrough**: Preserve child Git exit code

---

## Build & Packaging

**Gradle (sketch)**:

```kotlin
plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

application {
    mainClass.set("io.amichne.cli.MainKt")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
}

tasks.shadowJar {
    archiveBaseName.set("kapture")
    archiveClassifier.set("")
    minimize()
}
```

### Native Image (GraalVM)

Use GraalVM `native-image` to compile the CLI into a native binary for minimal startup latency.

**Command example**:

```bash
gu install native-image
./gradlew shadowJar
native-image --no-fallback --enable-http --enable-https -jar build/libs/kapture-all.jar kapture
```

**Output**: Single native binary `kapture` with startup times <10ms.

This is recommended for CI and developer installations. The JVM JAR remains supported for portability.

---

## Testing

### Unit Tests

- Regex extraction for ticket IDs
- Config loading precedence
- Interceptor mode logic (WARN/BLOCK/OFF)
- Session timeout logic
- Session continuity across branch switches

### Integration Tests

- Mock HTTP server for status API
- Temp Git repo for branch/commit/push flows
- Validate exit codes for each enforcement scenario
- Session tracking across multiple commands
- Session closure on branch switch
- Session timeout after inactivity

### Completion Tests

- Run `git --list-cmds=main` → verify real Git output
- Run `git help -a` → exits 0
- Run `git rev-parse --abbrev-ref HEAD` → works in test repo

---

## Security

- Never log credentials or full command lines
- Redact `GIT_ASKPASS`, `GIT_SSH_COMMAND`, tokens from telemetry
- Honor `trackingEnabled: false` for opt-out
- Local session file contains only branch/time data (no sensitive info)

---

## Observability

- `KAPTURE_DEBUG=1` → print interceptor decisions to stderr
- Default: silent unless WARN/BLOCK triggered
- Session tracking failures logged to `${config.localStateRoot}/tracking.log` (optional)
- All local data is stored under `${KAPTURE_LOCAL_STATE}` (default `$HOME/.kapture/state`)

---

## Implementation Checklist

1. ✅ Scaffold JVM Kotlin project with Gradle
2. ✅ Implement `Exec.passthrough()` and `Exec.capture()` via `ProcessBuilder`
3. ✅ Real Git resolver with recursion check
4. ✅ Config loader with JSON (kotlinx.serialization)
5. ✅ HTTP client setup (Ktor CIO)
6. ✅ Branch policy interceptor
7. ✅ Status gate interceptor
8. ✅ **Session-based time tracking interceptor** with local state management
9. ✅ Main CLI flow with bypass logic
10. ✅ Shadow JAR packaging
11. ✅ Smoke tests + completion validation + session tracking tests

---

## Uncertain Points → Decisions Made

1. **JVM overhead acceptable?** → Yes. Modern JVMs start fast enough for CLI use (~100ms).
2. **Native launcher needed?** → No. Shell script wrapper is sufficient.
3. **Ticket ID extraction from branch?** → Named capture group in regex: `(?<ticket>[A-Z]+-\d+)`.
4. **Missing ticket HTTP call?** → Fire request, treat 404 as missing, respect enforcement mode.
5. **Time tracking failures?** → Silent. Never block Git operations due to telemetry issues.
6. **Session timeout duration?** → Configurable via `sessionTrackingIntervalMs` (default 5 minutes).
7. **Session state persistence?** → Local JSON file (`${config.localStateRoot}/session.json`) with atomic writes.
8. **Multiple repos in same session?** → Each repo has independent session tracking (state file is global but branch
   name includes repo context).
9. **Ticket system support?** → Starts with Jira. Interface designed for multi-system compatibility (GitHub Issues,
   Trello, etc.).

---

**Key Change**: Time tracking now measures **session duration on branches** (for developer effort estimation) rather
than individual command execution time. Sessions are closed on branch switch or after inactivity timeout.

**This specification is ready for LLM-driven skeleton generation.**
