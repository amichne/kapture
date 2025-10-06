## Configuration

### Branch Pattern

The `branchPattern` in your Kapture config controls how branch names are validated and parsed:

```json
{
  "branchPattern": "^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$"
}
```

This pattern expects branches in the format: `TASK-123/description`

### Status Rules

Configure which Jira statuses are allowed for each workflow operation by customizing the status rules in your config:

```json
{
  "statusRules": {
    "allowCommitWhen": [
      "IN_PROGRESS",
      "READY"
    ],
    "allowPushWhen": [
      "READY",
      "IN_REVIEW"
    ]
  }
}
```

### Jira CLI Integration

Workflow commands require the `jira-cli` integration type:

```json
{
  "external": {
    "type": "jiraCli",
    "executable": "jira",
    "environment": {
      "JIRA_API_TOKEN": "<your-token>",
      "JIRA_EMAIL": "you@example.com"
    }
  }
}
```


## Quick Start

### Prerequisites

- JDK 21 for development builds (Temurin, GraalVM, Corretto…)
- Gradle wrapper ships with the repository (`./gradlew`)
- Optional: GraalVM 21 with the `native-image` component if you want the native binary

### Clone & Build

```bash
./gradlew shadowJar
```

The self-contained artefact is written to `cli/build/libs/kapture.jar`.

Run the wrapper with:

```bash
java -jar cli/build/libs/kapture.jar status
```

(Use `--args="..."` when executing via `./gradlew run`.)

### Install as a Git shim (JVM build)

1. Find the path to your real Git binary: `which git`.
2. Copy the fat JAR somewhere permanent (e.g. `/usr/local/lib/kapture/kapture.jar`).
3. Create a lightweight launcher script earlier on your `PATH`, **without** overwriting the system Git:

```bash
#!/usr/bin/env bash
export REAL_GIT="/usr/bin/git"      # replace with the path from step 1
command java -jar /usr/local/lib/kapture/kapture.jar "$@"
```

Save it as `~/bin/git`, mark executable (`chmod +x ~/bin/git`), and ensure `~/bin` precedes the system Git directory
in `PATH`.

4. Run your normal Git commands (`git status`, `git push`, …). Kapture forwards to the real Git binary after running
   its interceptors.

### Build and Install the Native Binary

1. Install GraalVM 21 and enable the native-image tool:

```bash
gu install native-image
```

2. From the project root build the native image:

```bash
./gradlew :cli:nativeCompile
```

The binary lands at `cli/build/native/nativeCompile/kapture` (or `kapture.exe` on Windows).

3. Drop the binary onto your `PATH` (e.g. `/usr/local/bin/kapture`) and reuse the shim listed above, swapping the
   `java -jar …` commandInvocation for the native binary path.

## Usage

- Everyday Git commands work unchanged (`git status`, `git commit -m`, `git push`). Kapture inspects the arguments to
  decide which interceptors to execute, then delegates to the resolved Git binary.
- Call the built-in helper at any time: `git kapture status` prints the resolved Git path and key configuration flags.
- `KAPTURE_DEBUG=1` increases logging (emitted to stderr) and preserves interceptor diagnostics.
- To supply configuration, create `~/.kapture/state/config.json` or point `KAPTURE_CONFIG` to a JSON file – see
  [`docs/architecture.md`](docs/architecture.md) for the schema. External integrations are polymorphic:

- REST API with bearer or Jira PAT auth

```json
{
  "external": {
    "type": "rest",
    "baseUrl": "https://your-jira.example.com/rest/api/3",
    "auth": {
      "type": "pat",
      "email": "you@example.com",
      "token": "<pat>"
    }
  }
}
```

- Jira CLI wrapper with environment-provided credentials

```json
{
  "external": {
    "type": "jiraCli",
    "executable": "jira",
    "environment": {
      "JIRA_API_TOKEN": "<pat>",
      "JIRA_EMAIL": "you@example.com"
    }
  }
}
```

## Troubleshooting

- Set `REAL_GIT` when the automatic resolver cannot find the real binary (WSL, custom installations, CI).
- Use `--version`, `--help`, completion flags, and `git config` unaffected; the wrapper fast-paths these commands.
- Network calls to your task system honour `trackingEnabled = false`; no session data is written in that mode.

## Documentation Map

- [`docs/architecture.md`](docs/architecture.md) – module breakdown, interceptor flow, HTTP integration
- [`docs/contributing.md`](docs/contributing.md) – developing new features, extending interceptors, testing standards

For repository conventions (style, testing, security), see `AGENTS.md`.
