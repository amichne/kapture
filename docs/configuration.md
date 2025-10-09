# Configuration

Kapture reads a single JSON document to discover how to enforce branch policy, talk to Jira, and locate the real Git
binary. This guide highlights the fields that matter most and shows how to adapt them for your workflow.

## Minimal template

```json
{ 
  "branchPattern": "^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$",
  "enforcement": {
    "branchPolicy": "WARN",
    "statusCheck": "OFF"
  },
  "statusRules": {
    "allowCommitWhen": [
      "TODO",
      "IN_PROGRESS"
    ],
    "allowPushWhen": [
      "REVIEW",
      "DONE"
    ]
  },
  "ticketMapping": {
    "default": "TODO",
    "providers": [
      {
        "provider": "jira",
        "rules": [
          {
            "to": "IN_PROGRESS",
            "match": [
              "In Progress",
              "Implementing"
            ]
          },
          {
            "to": "REVIEW",
            "match": [
              "In Review"
            ]
          },
          {
            "to": "BLOCKED",
            "match": [
              "Blocked"
            ]
          },
          {
            "to": "DONE",
            "match": [
              "Done",
              "Resolved"
            ]
          }
        ]
      }
    ]
  },
  "immediateRules": {
    "enabled": true,
    "optOutFlags": [
      "-nk",
      "--no-kapture"
    ],
    "optOutEnvVars": [
      "KAPTURE_OPTOUT"
    ],
    "bypassCommands": [
      "help",
      "--version",
      "--exec-path"
    ],
    "bypassArgsContains": [
      "--list-cmds"
    ]
  },
  "plugins": {
    "jira": {
      "type": "jiraCli",
      "environment": {
        "JIRA_USER_EMAIL": "dev@example.com",
        "JIRA_API_TOKEN": "<token>"
      }
    }
  },
  "trackingEnabled": false
}
```

> Place this file at `~/.kapture/state/config.json` or point `KAPTURE_CONFIG` at an alternative path.

## Key sections

<details>
<summary>Branch pattern & naming</summary>

- `branchPattern` must include a named capture group `task`; Kapture extracts that group to resolve the Jira ticket ID.
- Use anchors (`^`, `$`) to keep matching fast. Need multiple conventions? Compose them with non-capturing groups:
  `^(?<task>(ENG|OPS)-\d+)\/(feature|fix)\/.*$`.
- When introducing a new pattern, run `git status` to confirm the compiled regex loads without errors (check Kapture
  section).

</details>

<details>
<summary>Enforcement modes</summary>

| Mode    | Behaviour                                                    |
|---------|--------------------------------------------------------------|
| `OFF`   | Skip the interceptor entirely.                               |
| `WARN`  | Print diagnostics but allow the Git command to run.          |
| `BLOCK` | Abort the Git command immediately with a non-zero exit code. |

Configure branch policy and status checks independently via `enforcement.branchPolicy` and `enforcement.statusCheck`.

</details>

<details>
<summary>Status gates</summary>

- `statusRules.allowCommitWhen` and `statusRules.allowPushWhen` accept internal status names (e.g. `TODO`,
  `IN_PROGRESS`).
- Feed them the values produced by your `ticketMapping`; comparisons are case-sensitive.
- Leave a list empty to block the action outright, or omit `statusRules` entirely to fall back to built-in defaults.

</details>

<details>
<summary>Ticket mapping</summary>

- Converts provider-specific status strings into Kapture's internal status enum.
- Rules are evaluated in order; the first match wins. Set `regex: true` to enable full-string regular expressions.
- `default` fills in when no rule matches; set it to `null` to treat unknown statuses as "no status".

```jsonc
"ticketMapping": {
  "default": "TODO",
  "providers": [
    {
      "provider": "jira",
      "rules": [
        { "to": "IN_PROGRESS", "match": ["In Progress", "Implementing"] },
        { "to": "REVIEW",      "match": ["In Review"] },
        { "to": "BLOCKED",     "match": ["Blocked"] },
        { "to": "DONE",        "match": ["Done", "Resolved"] }
      ]
    }
  ]
}
```

</details>

<details>
<summary>Immediate rules</summary>

- Control how the shim behaves for normal git verbs (checkout, commit, push, ...).
- `enabled` toggles the entire interceptor pipeline. Opt-out flags/env-vars skip enforcement but still forward the
  command to Git.
- `bypassCommands` / `bypassArgsContains` keep help/completion/version flows fast by skipping the interceptors entirely.

```jsonc
"immediateRules": {
  "enabled": true,
  "optOutFlags": ["-nk", "--no-kapture"],
  "optOutEnvVars": ["KAPTURE_OPTOUT", "GIRA_NO_KAPTURE"],
  "bypassCommands": ["help", "--version", "--exec-path"],
  "bypassArgsContains": ["--list-cmds"]
}
```

</details>

<details>
<summary>External integrations</summary>

Kapture normalises integrations through the `external` block.

```jsonc
{
  "plugins": {
  "jira": {
    "type": "jiraCli",
    "executable": "jira",
    "environment": {
      "JIRA_USER_EMAIL": "dev@example.com",
      "JIRA_API_TOKEN": "<token>",
      "JIRA_SERVER": "https://jira.example.com"
    }
  }
}
```

- `jiraCli` shells out to the official [`jira-cli`](https://github.com/ankitpokhrel/jira-cli) binary and expects
  credentials via environment variables (keep tokens in a secrets manager, not in source control).
- The experimental `rest` adapter targets Jira Cloud/Data Center REST APIs. Supply a `baseUrl` and an `auth` block
  describing either PAT or basic auth. See the source for current capabilities before adopting.

</details>

<details>
<summary>Telemetry controls</summary>

- `trackingEnabled = true` emits session snapshots through `ExternalClient.trackSession`.
- `sessionTrackingIntervalMs` throttles how often a new snapshot is emitted (default: 30s).
- When disabled, interceptors skip snapshot generation entirely.

</details>

## Configuration precedence

Kapture loads configuration in the following order (first match wins):

1. **Command-line flags**: `-k <path>` or `--konfig <path>` or `--konfig=<path>`
   ```bash
   git -k /path/to/custom-config.json commit -m "message"
   git --konfig=/path/to/config.json status
   ```
2. **Environment variable**: `KAPTURE_CONFIG=/path/to/config.json`
3. **Default location**: `~/.kapture/state/config.json`

## Environment variables

| Variable          | Purpose                                                     |
|-------------------|-------------------------------------------------------------|
| `KAPTURE_CONFIG`  | Absolute path to the config file.                           |
| `KAPTURE_DEBUG`   | When truthy, prints verbose diagnostics to stderr.          |
| `REAL_GIT`        | Explicit path to the real Git binary (overrides discovery). |
| `JIRA_USER_EMAIL` | Default Jira username/email when using `jiraCli`.           |
| `JIRA_API_TOKEN`  | Authentication token for Jira integrations.                 |
| `KAPTURE_OPTOUT`  | When set, skip all interceptors (useful in CI jobs).        |

Values in the environment always win over file defaults.

## File locations

```text
~/.kapture/
├── state/
│   └── config.json    # default config path
└── logs/              # optional debug output when enabled
```

Share configs across teams by:

- Checking them into your repo and using `-k path/to/team-config.json`
- Setting `KAPTURE_CONFIG` in shell profiles or CI job definitions
- Creating per-project configs and using command-line flags for different contexts

## Change management tips

- Store custom regexes and status rules in version control so they evolve with your workflow.
- Validate updates in CI by running [`scripts/integration-test.sh`](../scripts/integration-test.sh); it exercises branch
  policy, status gates, and jira-cli interactions end-to-end.
- Pair config releases with documentation updates in [`docs/workflow-automation.md`](workflow-automation.md) so your
  team knows how behaviour changed.
