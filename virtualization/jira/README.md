# Jira Mock Server

A minimal Jira façade backed by static JSON fixtures. It responds to a curated set of REST endpoints – just enough to
exercise the Kapture CLI in local development and CI pipelines without a full Jira deployment.

## Behaviour at a glance

- Seeds data from the JSON files in `virtualization/jira/resources` (projects, boards, issues, fields, etc.).
- Writes changes (new issues, status transitions) to `virtualization/jira/data/state.json` by default. Delete the file
to reset back to the seed data.
- Implements the routes referenced by the fixtures (create meta, search, issue read/write, transitions, boards/sprints,
  users, releases). Unhandled routes return `404` with a short message.

## Running locally

```bash
cd virtualization/jira
npm install
npm start
```

Environment variables:

| Variable          | Description                                                      | Default                          |
|-------------------|------------------------------------------------------------------|----------------------------------|
| `PORT`            | Listen port                                                       | `8080`                           |
| `JIRA_MOCK_PORT`  | Alternative way to set the listen port                            |                                  |
| `JIRA_MOCK_BASE_URL` | Base URL used when constructing `self` links                    | `http://localhost:PORT`          |
| `JIRA_MOCK_DB`    | Path to the persisted JSON state                                  | `./data/state.json`              |

## Docker / Compose

```bash
# Build the image
cd virtualization/jira
npm install
docker build -t kapture-jira-mock .

# Or via docker compose (preferred)
docker compose -f ../stack/docker-compose.yml up -d
```

The integration harness (`scripts/integration-test.sh`) uses the compose file under `virtualization/stack` and assumes
port 8080.

## Extending the mock

1. Add or edit fixtures under `virtualization/jira/resources`.
2. Update `server.js` to serve the corresponding route (most reads are simple lookups; writes persist back to the state
   file).
3. Restart the container or the local server.

Keep responses small and deterministic – the goal is smoke testing, not feature parity with Jira.
