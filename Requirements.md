# Design: Kotlin Multiplatform Git Wrapper with Interactive Autocomplete Preservation (Linux + macOS)

## Goal

Ship a tiny, native `git`-compatible CLI that:

* Proxies every command with identical UX, including **shell autocomplete** and interactive flows.
* Intercepts selected verbs for **branch policy**, **ticket status gating**, and **time tracking**.
* Targets **Linux x64** and **macOS (x64 + arm64)** via **Kotlin Multiplatform (KMP)**.
* Has zero runtime deps (no JRE). Small artifacts for CI and developer installs.

---

## Architecture Overview

### Modules

* `:core-common` (KMP common + per-target actuals)
  Logic, config models, interceptors, registry, process API (expect/actual), HTTP client.
* `:cli` (KMP native executables for linuxX64, macosX64, macosArm64)
  Entry point, binary packaging.
* `:interceptors` (optional split; can live in core-common)
  Branch policy, status gating, time tracking.
* `:commands` (optional)
  Custom `git gira <subcommand>` commands.

### Process model

* Two execution paths:

  * **Passthrough path (TTY inherited)** for nearly all invocations. Ensures completion and interactions work.
  * **Capture path** only for short, non-interactive helper calls we need to **inspect** (e.g., `rev-parse` to get the
    current branch). Never used for user-invoked top-level commands.

### Interception policy (minimal and safe)

* **Before** `git` spawn: evaluate rules. If violation and mode=BLOCK, exit with code and message. If WARN, print and
  continue.
* **After** child exit: post time tracking event.
* Never alter args or environment for passthrough.

---

## Autocomplete Preservation

### What completion needs

Shell completion uses `git` to query:

* `git --list-cmds=main,others,alias,nohelpers`
* `git help -a` and `--no-pager help -a`
* Query plumbing: `rev-parse`, `config`, `for-each-ref`, etc.

### Rules

* **Absolute passthrough** for:
  `--list-cmds=*`, `help`, `--version`, `--exec-path`, `config`, `rev-parse`, `for-each-ref`.
  Do not run interceptors for these.
* **TTY inheritance**: the spawned `git` must share the controlling terminal. Use `fork`+`execvp` (Unix). No pipes. No
  PTY required for completion itself, but TTY inheritance is required for interactive flows (`add -p`, editor, pager).
* Preserve env: `GIT_*`, `PAGER`, `LESS`, `EDITOR`, `VISUAL`, `SSH_ASKPASS`, `GIT_SSH_COMMAND`, locale.

---

## Kotlin Multiplatform Plan

### Targets

* `linuxX64`
* `macosX64`
* `macosArm64`
* Optional `jvm` target for local debugging.

### Dependencies

* Serialization: `org.jetbrains.kotlinx:kotlinx-serialization-json`
* HTTP: Ktor client

  * Common: `ktor-client-core`
  * Native: `ktor-client-curl` (requires libcurl; present on macOS; typical on CI Linux)
  * JVM (optional): `ktor-client-cio`

### Expect/Actual Interfaces

#### Process + TTY

```kotlin
// commonMain
data class ExecResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

expect object Exec {
    /** Inherit current stdin/stdout/stderr. Blocking. Returns exit code. */
    fun passthrough(
        cmd: List<String>,
        workDir: String? = null,
        env: Map<String, String> = emptyMap()
    ): Int

    /** Capture stdout/stderr via pipes. Use only for short helper commands. */
    fun capture(
        cmd: List<String>,
        workDir: String? = null,
        env: Map<String, String> = emptyMap()
    ): ExecResult
}

expect object Platform {
    fun osHintsRealGit(): List<String>   // e.g., /usr/bin/git, /opt/homebrew/bin/git
    val shell: List<String>              // e.g., ["/bin/sh","-lc"]
}
```

#### Native actuals (Linux/macOS)

* **Passthrough**: `fork` → `execvp` with inherited FDs (`STDIN_FILENO`, `STDOUT_FILENO`, `STDERR_FILENO`). `waitpid`for
  exit code.
* **Capture**: `pipe` + `fork` + `dup2` for stdout/stderr only. Never used for interactive or completion flows.

Pseudo-implementation notes:

* Build command through `execvp` directly when `cmd[0]` is absolute or resolvable from PATH.
* If using a shell, rely on `"/bin/sh", "-lc", <joined string>` only in capture helpers; **avoid shell** for passthrough
  to keep signals and TTY behavior correct.
* Set env with `posix_spawnattr`/`execve` variants if needed. Otherwise inherit full env except optional `GIT_TRACE`,
  etc. Do not mutate by default.

#### macOS codesigning

* Not required for local use. Provide optional developer instruction to
  `codesign --force --sign - --timestamp=none <binary>` for corporate environments.

---

## Real Git Resolution

Order:

1. `REAL_GIT` env overrides everything.
2. Config `realGitHint` if set.
3. `command -v git` via **capture** path.
4. Platform hints from `Platform.osHintsRealGit()`.

Prevent recursion:

* If wrapper is named `git`, compare its full path to candidate; skip if same inode/path.

---

## CLI Entry Flow

```kotlin
fun main(argv: Array<String>) {
    val args = argv.toList()
    val cfg = Config.load()
    val realGit = resolveRealGit(cfg.realGitHint)
    val ext = ExternalClient(cfg.externalBaseUrl, cfg.apiKey, cfg.trackingEnabled)

    // Custom subcommands: `git gira <name>`
    if (args.firstOrNull()?.equals("gira", ignoreCase = true) == true) {
        runGiraCommand(args.drop(1), cfg, ext, realGit)
        return
    }

    // Completion and help guardrails: skip interceptors
    if (isCompletionOrHelpPath(args)) {
        exit(Exec.passthrough(listOf(realGit) + args))
    }

    val inv = Invocation(args, args.firstOrNull()?.lowercase(), realGit)

    // Before hooks
    for (i in Registry.interceptors) {
        if (i.matches(inv)) i.before(inv)?.let { exit(it) }
    }

    // Passthrough spawn with TTY inheritance
    val childCode = Exec.passthrough(listOf(realGit) + args)

    // After hooks
    var code = childCode
    for (i in Registry.interceptors) {
        if (i.matches(inv)) code = i.after(inv, code)
    }
    exit(code)
}
```

`isCompletionOrHelpPath(args)` returns true for:

* `args.contains("--list-cmds")`
* first == `help`
* first == `--version` or `--exec-path`
* `rev-parse` when invoked alone by completion? Recommendation: allow passthrough but **do not** block.

---

## Config

Location priority:

1. `GITWRAP_CONFIG` env → absolute path
2. `~/.git-wrapper/config.json`
3. defaults

Model:

```kotlin
@Serializable
data class Enforcement(
    val branchPolicy: Mode = Mode.WARN,
    val statusCheck: Mode = Mode.WARN
) {
    @Serializable
    enum class Mode { WARN, BLOCK, OFF }
}

@Serializable
data class StatusRules(
    val allowCommitWhen: Set<String> = setOf("IN_PROGRESS", "READY"),
    val allowPushWhen: Set<String> = setOf("READY", "IN_REVIEW")
)

@Serializable
data class Config(
    val externalBaseUrl: String = "http://localhost:8080",
    val apiKey: String? = null,
    val branchPattern: String = "^(?<ticket>[A-Z]+-\\d+)\\/[a-z0-9._-]+$",
    val enforcement: Enforcement = Enforcement(),
    val statusRules: StatusRules = StatusRules(),
    val trackingEnabled: Boolean = true,
    val realGitHint: String? = null
)
```

---

## Interceptors

### 1) Branch Policy

* Trigger on: `checkout -b/-B`, `switch -c/--create`, `branch -c/-C`.
* Validate `branchPattern`.
* Optionally verify ticket existence via `GET /tickets/{id}/status`. If missing:

  * WARN: stderr note, continue.
  * BLOCK: exit code 2.

### 2) Status Gate

* Trigger on: `commit`, `push`.
* Get current branch via **capture**: `git rev-parse --abbrev-ref HEAD`.
* Extract `ticket` from pattern. Query status. Compare against `StatusRules` per verb.
* WARN: note. BLOCK: exit 3/4.

### 3) Time Tracking

* Start timestamp at `before`.
* On `after`, compute duration. Post:
  `POST /events/track` with `{ command, args, branch?, ticket?, durationMs }`.
* Fire-and-forget. Fail silently.

**Do not** intercept:

* `help`, `--list-cmds=*`, `--version`, `--exec-path`, `config --get-regexp .*`, `for-each-ref`.

---

## Commands (`git gira <cmd>`)

* Registry-based. No JVM `ServiceLoader`.

```kotlin
interface Command {
    val name: String;fun run(
        ctx: Context,
        args: List<String>
    ): Int
}
object Registry {
    val commands: List<Command> = listOf(Hello())  // extend here
    val interceptors: List<(Env) -> Interceptor> = listOf(::BranchPolicy, ::StatusGate, ::TimeTrack)
}
```

---

## HTTP Client

* Ktor `client-curl` with `ContentNegotiation` (JSON).
* Timeouts: connection + request 10s default.
* Auth: `Authorization: Bearer <apiKey>` when set.
* Error handling: treat non-2xx as absent status. Never block passthrough unless policy says BLOCK.

---

## Error Handling and Exit Codes

* Preserve child `git` exit code on success path.
* Interceptor blocks:

  * Branch policy fail: 2
  * Status gate commit fail: 3
  * Status gate push fail: 4
* Wrapper internal failure (e.g., cannot find real git): 127 (conventional command-not-found), with clear stderr.

---

## Security and Privacy

* Never log credentials or full command lines to remote.
* Redact env keys on telemetry (`GIT_ASKPASS`, `GIT_SSH_COMMAND`, tokens).
* Allow opt-out: `"trackingEnabled": false`.

---

## Performance

* Passthrough is O(1) overhead beyond a fork/exec.
* Capture used only for tiny helpers (`rev-parse`). Cache branch name for the current PID for the duration of a single
  wrapper invocation to avoid duplicate calls.

---

## Build and Packaging

### Gradle (sketch)

```kotlin
plugins {
    kotlin("multiplatform") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
}

kotlin {
    linuxX64 { binaries { executable { entryPoint = "io.amichne.cli.main" } } }
    macosX64 { binaries { executable { entryPoint = "io.amichne.cli.main" } } }
    macosArm64 { binaries { executable { entryPoint = "io.amichne.cli.main" } } }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
                implementation("io.ktor:ktor-client-core:2.3.12")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
            }
        }
        val nativeMain by creating {
            dependencies { implementation("io.ktor:ktor-client-curl:2.3.12") }
        }
        val linuxX64Main by getting { dependsOn(nativeMain) }
        val macosX64Main by getting { dependsOn(nativeMain) }
        val macosArm64Main by getting { dependsOn(nativeMain) }
    }
}
```

### Outputs

* `cli/build/bin/<target>/releaseExecutable/cli.kexe`
  Rename to `git` if you replace. Or install as `gira` and put **before** real git in PATH.
* Strip symbols: Gradle `binaries.executable { freeCompilerArgs += listOf("-Xbinary=stripped") }`
* Optional UPX after CI builds where allowed.

---

## CI Integration

* Matrix build: macos-14 (arm64), macos-13 (x64), ubuntu-22.04 (x64).

* Produce zips:

  ```
  gira-klient-<os>-<arch>.zip
    /bin/git               # the wrapper binary
    /LICENSE
    /README.md
  ```

* Optional: attach default `config.json.example`.

* Smoke tests in CI:

  * `git --version` returns real git’s version.
  * `git --list-cmds=main` contains expected tokens.
  * `git help -a` exits 0.
  * `git rev-parse --abbrev-ref HEAD` works in a temp repo.
  * Interceptor WARN mode emits to stderr but exit 0 for allowed flows.
  * BLOCK mode returns defined exit code.

---

## Test Plan

### Unit

* Regex ticket extraction.
* Config load precedence.
* Interceptor decision tables.

### Integration (headless)

* Temp repo with test remotes.
  Use `GIT_DIR`, `GIT_WORK_TREE` in a temp folder.
* Validate branch creation enforcement across:

  * `checkout -b`, `switch -c`, `branch -c/-C`.
* Validate commit/push gates with mocked HTTP server.

### TTY and Interactive

* Use `script` or `scriptreplay` (Linux) and `script` (macOS) to ensure:

  * `git add -p` prompts appear.
  * `git commit` opens `$EDITOR` when no `-m`.
  * Pagers activate (`git log` pipes to less).
* Expect success with passthrough inheritance; no PTY emulation required.

### Completion

* Install git-completion for bash/zsh in CI runner.
* Assertions:

  * `__git_wrap__test` calls to `git --list-cmds` succeed.
  * `git <TAB>` lists commands.
  * `git checkout <TAB>` completes branches.

---

## Edge Cases

* Wrapper named `git` placed before real git in PATH:
  * Resolution must skip self. Compare path and inode if available.

* Users with custom aliases relying on `!` shell commands:
  * No change; aliases interpreted by real git.

* GPG signing:
  * Inherit TTY; ensure `GPG_TTY=$(tty)` respected. Do not filter env.

* SSH prompts:
  * Inherit TTY; `ssh` will prompt normally.

---

## Observability

* Local debug flag: `GIRA_DEBUG=1` prints wrapper decisions to stderr.
* Never print in normal operation unless WARN/BLOCK or explicit debug.

---

## Migration and Rollout

* Phase 1: ship as `gira` binary. Ask devs to run `git gira doctor` to verify env and show resolved real git.
* Phase 2: symlink `git` → wrapper in a controlled PATH segment per team.
* Phase 3: enforce policies incrementally: start `WARN`, then per-repo move to `BLOCK`.

---

## Implementation Checklist

1. KMP scaffolding and targets.
2. `Exec.passthrough` native actual via `fork`/`execvp` + `waitpid`.
3. `Exec.capture` native actual via `pipe` + `dup2` for `STDOUT_FILENO` and `STDERR_FILENO`.
4. Real git resolver with recursion avoidance.
5. Interceptors (BranchPolicy, StatusGate, TimeTrack) with minimal external calls.
6. Autocomplete and help **bypass** list wired.
7. HTTP client (Ktor curl) with JSON.
8. Config loader with env override.
9. Packaging: per-target stripped executables.
10. CI matrix, smoke tests, interactive script tests, completion tests.
