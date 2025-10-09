# Kapture Virtualization

Docker-based development and testing environment for Kapture.

## Quick Start

### Start All Services
```bash
cd virtualization
docker compose up -d
```

### Stop All Services
```bash
docker compose down
```

## Available Services

| Service | Description |
|---------|-------------|
| `jira`  | Mock Jira REST + CLI hybrid server |

## Configuration

The mock server exposes both REST and CLI-compatible behaviours. Update your Kapture config to use both plugins for richer demos:

```jsonc
{
  "plugins": {
    "jira-rest": {
      "type": "REST",
      "baseUrl": "http://localhost:8080",
      "auth": { "type": "bearer", "token": "mock-token-not-required" },
      "provider": "jira"
    },
    "jira-cli": {
      "type": "CLI",
      "paths": ["jira"],
      "environment": {
        "JIRA_USER_EMAIL": "demo@example.com",
        "JIRA_API_TOKEN": "mock-token"
      },
      "provider": "jira"
    }
  },
  "branchPattern": "^(?<task>[A-Z]+-\d+)/[a-z0-9._-]+$",
  "enforcement": {
    "branchPolicy": "WARN",
    "statusCheck": "WARN"
  }
}
```

Use `git -k virtualization/jira/sample-config.json status` to try it immediately.
