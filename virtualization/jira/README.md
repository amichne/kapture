# Jira Mock Server

This directory hosts a lightweight mock implementation of the Jira REST API defined by `openapi.json`. It is intended for local development and automated tests where spinning up a full Jira instance is impractical.

## Getting started

```bash
cd virtualization/jira
npm install
npm start
```

By default the server listens on port `8080` and seeds a handful of randomized projects and issues. The following environment variables can be used to influence the generated data:

- `PORT` or `JIRA_MOCK_PORT` – override the listen port (defaults to `8080`).
- `JIRA_MOCK_BASE_URL` – base URL used when constructing `self` links in responses (defaults to `http://localhost:8080`).
- `JIRA_MOCK_SEED` – when set to a number, produces deterministic test data.
- `JIRA_MOCK_PROJECTS` – number of projects to generate (defaults to 3).
- `JIRA_MOCK_ISSUES` – number of top-level issues per project (defaults to 8).

## Docker

A container image can be built with Docker using the provided Dockerfile:

```bash
cd virtualization/jira
docker build -t kapture-jira-mock .
docker run -p 8080:8080 kapture-jira-mock
```

The compose stack in `virtualization/stack/docker-compose.yml` references this Dockerfile and exposes the same environment variables for customization.
