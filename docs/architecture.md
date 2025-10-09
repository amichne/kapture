# Architecture

Kapture is split into composable modules so policy checks, external integrations, and build targets can evolve
independently. This guide orients you around the moving parts and shows how a single Git command flows through the
system.

## TL;DR

- `core` owns shared primitives: configuration loading, process execution, logging, and the external client facade.
- `interceptors` contribute policy hooks that run before/after Git executes.
- `cli` wires everything together, resolves the real Git binary, routes custom git commands, and produces runnable artefacts.

## System map

```mermaid
flowchart TD
    subgraph CLI
        Entry[Entry point\nMainKt]
        Registry[InterceptorRegistry]
    end

    subgraph Core
        Config[Config loader]
        Exec[CommandExecutor]
        External[ExternalClient]
    end

    subgraph Interceptors
        Branch[BranchPolicy]
        Status[StatusGate]
        Commit[CommitMessage]
        Session[SessionTracking]
    end

    Entry --> Config
    Entry --> Registry
    Registry --> Branch
    Registry --> Status
    Registry --> Commit
    Registry --> Session
    Branch --> Exec
    Status --> Exec
    Commit --> Exec
    Session --> Exec
    Exec -->|git| RealGit[(Real git)]
    Status --> External
    Session --> External
    External --> Jira[(Jira CLI / REST)]
```

Each interceptor depends only on `core` types and is registered declaratively inside `Registry`. The CLI orchestrates
order; the core module supplies the runtime services they rely on.

## Module responsibilities

<details>
<summary><code>core</code></summary>

- Loads configuration (`Config.load`) with precedence: explicit path → `KAPTURE_CONFIG` → default state directory.
- Resolves the real Git binary via `RealGitResolver`, filtering out the wrapper itself.
- Provides `CommandExecutor` helpers for passthrough and captured execution.
- Hosts the `ExternalClient` abstraction that normalises task status queries and telemetry submission across adapters.

</details>

<details>
<summary><code>interceptors</code></summary>

- Define the `GitInterceptor` contract (`before`/`after` hooks).
- Ship built-in implementations:
  - `BranchPolicyInterceptor` – validates branch names against the configured regex.
  - `StatusGateInterceptor` – checks ticket state before critical commands (commit, push).
  - `CommitMessageInterceptor` – automatically prepends ticket key to commit messages.
  - `SessionTrackingInterceptor` – emits activity snapshots when enabled.
- Register interceptors in processing order via `InterceptorRegistry.interceptors`.

</details>

<details>
<summary><code>cli</code></summary>

- Parses the incoming Git invocation and routes commands:
  - Custom git commands (`git subtask`, `git start`, `git review`, `git merge`, `git work`) execute directly.
  - Git status and help commands run normally, then Kapture appends its own info to the output.
  - Standard git commands forward through the interceptor pipeline.
- Applies fast paths for read-only commands (e.g. `--version`, completions) to keep Git UX snappy.
- Contains `WorkflowCommands` for Jira workflow automation (subtask creation, PR management, etc.).
- Exposes build artefacts: Kotlin/JVM application, GraalVM native image, and Docker entrypoints.

</details>

## Invocation lifecycle

1. `MainKt.main` receives the raw Git arguments.
2. `Config.load` resolves settings and materialises the state directory if needed.
3. `RealGitResolver` searches `REAL_GIT`, config hints, and `PATH`, stopping once it finds a binary that is not the
   wrapper itself.
4. The CLI decides which path to take:
   - **Custom git commands** (`git subtask`, `git start`, `git review`, `git merge`, `git work`) route to `WorkflowCommands` immediately;
   - **Status/help commands** (`git status`, `git help`) run git first, then append Kapture info;
   - **Read-only Git commands** (completions, `--version`, `--list-cmds`) bypass interceptors;
   - **Everything else** becomes a `CommandInvocation` that flows through the interceptor pipeline.
5. Each interceptor's `before` hook runs in order and may short-circuit by returning a non-null exit code.
   - Example: `CommitMessageInterceptor` modifies the commit message and executes git directly, preventing duplicate commits.
6. If execution proceeds, `CommandExecutor.passthrough` spawns the real Git process with untouched environment and
   working directory.
7. After Git exits, `after` hooks run in the same order (session tracking, post-run messaging, …).
8. The CLI returns Git's exit code to the caller.

## External integrations

`ExternalClient` exposes two composable operations to interceptors:

- `getTaskStatus(taskId)` – fetches ticket state from Jira (via `jira-cli` or REST).
- `trackSession(snapshot)` – forwards telemetry events when tracking is enabled.

Adapters handle retries, logging, and command execution. When integrations fail, interceptors degrade gracefully and
allow Git to continue, logging diagnostics behind `KAPTURE_DEBUG`.

## Native image build pipeline

The CLI module uses `org.graalvm.buildtools.native` and enables optimisation flags tuned for CI and local runs:

```kotlin
buildArgs.addAll(
    "--pgo", "--strict-image-heap",
    "-R:MaxHeapSize=512m", "-march=native", "-Ob"
)
```

Relevant Gradle tasks:

- `:cli:nativeCompile` – build the optimised native binary.
- `:cli:nativeRun` – execute the binary directly from the build output.
- `:cli:nativeTest` – run JVM tests against the native runtime (when supplied).

## Extending the pipeline

### Adding new interceptors

1. Implement `GitInterceptor` in the `interceptors` module:
   - Return a non-null exit code from `before` to block the original Git command.
   - Use `CommandInvocation.passthroughGit()` or `captureGit()` to execute modified commands.
2. Register your interceptor inside `InterceptorRegistry.interceptors`, paying attention to ordering:
   - Policy checks (branch, status) run first.
   - Command modifiers (commit message) run before execution.
   - Tracking/telemetry runs last.
3. Document any new configuration keys in [`docs/configuration.md`](configuration.md).
4. Add tests under `interceptors/src/test/kotlin`.

### Adding new workflow commands

1. Add command handler in `WorkflowCommands.kt` (in `cli` module).
2. Register the command in `Main.kt` custom command routing:
   ```kotlin
   if (customCommand in setOf("subtask", "start", "review", "merge", "work", "yourcmd")) {
       runCustomGitCommand(customCommand, ...)
   }
   ```
3. Update `runCustomGitCommand` when-expression to route to your handler.
4. Document the command in [`docs/workflow-automation.md`](workflow-automation.md).

Keep shared utilities in `core` so other interceptors and commands can reuse them without creating new dependencies.
