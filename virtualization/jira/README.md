# Jira Mock Server

A lightweight mock Jira server for testing and demonstrating Kapture without requiring a real Jira instance.

## Features

- ✅ **Jira REST API v3** endpoints for core operations
- ✅ **Web UI** for managing mock issues
- ✅ **In-memory storage** with ability to reset to defaults
- ✅ **Issue creation** with subtask support
- ✅ **Status transitions** (To Do → In Progress → Code Review → Done)
- ✅ **JQL search** (basic support)
- ✅ **Zero configuration** - works out of the box
- ✅ **Docker support** - run with one command

## Quick Start

### Option 1: Docker (Recommended)

The easiest way to run the mock server is using Docker Compose:

```bash
# From the repository root
cd virtualization
docker compose up -d

# Check logs
docker compose logs -f jira

# Stop the server
docker compose down
```

The server will be available at `http://localhost:8080`

**What you get:**
- ✅ No need to install Node.js
- ✅ Consistent environment across machines
- ✅ Easy to share and distribute
- ✅ Health checks and auto-restart
- ✅ Isolated network environment

### Option 2: Local Node.js

#### 1. Install Dependencies

```bash
cd virtualization/jira
npm install
```

#### 2. Start the Server

```bash
npm start
```

The server will start on `http://localhost:8080`

#### 3. Open the Admin UI

Open your browser to [http://localhost:8080](http://localhost:8080)

You'll see a dashboard showing all mock issues with the ability to:
- Create new issues
- Edit existing issues
- Delete issues
- Reset to default data

## Configure Kapture

Update your Kapture config to point to the mock server:

```json
{
  "externalBaseUrl": "http://localhost:8080",
  "branchPattern": "^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$",
  "enforcement": {
    "branchPolicy": "WARN",
    "statusCheck": "WARN"
  },
  "statusRules": {
    "allowCommitWhen": ["TODO", "IN_PROGRESS"],
    "allowPushWhen": ["REVIEW", "DONE"]
  },
  "ticketMapping": {
    "default": "TODO",
    "providers": [
      {
        "provider": "jira",
        "rules": [
          { "to": "IN_PROGRESS", "match": ["In Progress"] },
          { "to": "REVIEW", "match": ["Code Review"] },
          { "to": "BLOCKED", "match": ["Blocked"] },
          { "to": "DONE", "match": ["Done"] }
        ]
      }
    ]
  },
  "external": {
    "type": "rest",
    "baseUrl": "http://localhost:8080",
    "auth": {
      "type": "bearer",
      "token": "mock-token"
    }
  },
  "trackingEnabled": false
}
```

Or use the `-k` flag to point to a custom config:

```bash
git -k /path/to/mock-config.json status
```

## Supported Endpoints

### Jira REST API v3

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/rest/api/3/issue/:key` | GET | Get issue details |
| `/rest/api/3/issue` | POST | Create new issue |
| `/rest/api/3/issue/:key/transitions` | GET | Get available transitions |
| `/rest/api/3/issue/:key/transitions` | POST | Perform status transition |
| `/rest/api/3/search` | GET | Search issues (basic JQL) |
| `/rest/api/3/myself` | GET | Get current user info |

### Admin API

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/issues` | GET | Get all issues |
| `/api/admin/issues/:key` | PUT | Update issue |
| `/api/admin/issues/:key` | DELETE | Delete issue |
| `/api/admin/reset` | POST | Reset to default data |

## Testing Kapture Workflows

### 1. Create a Story

Using the web UI, create a story:
- **Summary**: "Implement password reset"
- **Type**: Story
- **Status**: To Do
- **Key**: TEST-100 (auto-generated)

### 2. Start Work on the Story

```bash
git start TEST-100
```

This will:
- Create a branch `TEST-100/implement-password-reset`
- Transition TEST-100 to "In Progress"

### 3. Create a Subtask

```bash
git subtask TEST-100 "Add validation logic"
```

This will:
- Create a subtask under TEST-100
- Transition the subtask to "In Progress"
- Return the subtask key (e.g., TEST-101)

### 4. Make Commits

```bash
git commit -m "Add password validation"
# Actual message: "TEST-101: Add password validation"
```

### 5. Create a Pull Request

```bash
git review
```

This will:
- Push the branch
- Create a PR
- Transition TEST-101 to "Code Review"

### 6. Merge the PR

```bash
git merge --close-parent
```

This will:
- Merge the PR
- Transition TEST-101 to "Done"
- Transition TEST-100 to "Done"

## JQL Search Examples

The mock server supports basic JQL queries:

```bash
# Search by project
curl "http://localhost:8080/rest/api/3/search?jql=project=TEST"

# Search by status
curl "http://localhost:8080/rest/api/3/search?jql=status=\"In Progress\""

# Search by key
curl "http://localhost:8080/rest/api/3/search?jql=key=TEST-1"
```

## Status Transitions

The mock server supports the following status transitions:

```
To Do → In Progress, Done
In Progress → To Do, Code Review, Done
Code Review → In Progress, Done
Done → In Progress
Blocked → In Progress, To Do
```

## Sample Data

The server loads initial data from `resources/search.json`:
- TEST-1: Bug (To Do)
- TEST-2: Story (In Progress)
- TEST-3: Task (Done)

You can reset to this data at any time using the "Reset to Default" button in the UI.

## Development

### Run with Auto-Reload

```bash
npm run dev
```

This will restart the server automatically when you make changes.

### Environment Variables

- `PORT` - Server port (default: 8080)

```bash
PORT=3000 npm start
```

## Troubleshooting

### Port Already in Use

If port 8080 is already in use, set a different port:

```bash
PORT=3001 npm start
```

Then update your Kapture config to use the new port.

### Issues Not Loading

Check the server logs for errors. The server logs all API requests:

```
[GET] /rest/api/3/issue/TEST-1
[POST] /rest/api/3/issue/TEST-1/transitions
```

### Reset Not Working

Stop and restart the server:

```bash
# Ctrl+C to stop
npm start
```

## Docker

### Building and Running

```bash
# Build and start (detached mode)
cd virtualization
docker compose up -d

# View logs
docker compose logs -f jira

# Stop
docker compose down

# Rebuild after changes
docker compose up -d --build
```

### Docker Troubleshooting

**Port conflict:**
```bash
# Change port in docker-compose.yml
ports:
  - "3000:8080"  # Change 8080 to 3000 on host
```

**Check container status:**
```bash
docker compose ps
docker compose logs jira
```

**Access container shell:**
```bash
docker compose exec jira sh
```

**Health check:**
```bash
# Should return "healthy"
docker inspect kapture-jira-mock | grep -i health
```

### CI/CD Integration

Use the Docker image in your CI/CD pipelines:

```yaml
# GitHub Actions example
services:
  jira:
    image: kapture-jira-mock
    ports:
      - 8080:8080
```

Or use docker compose directly:

```bash
# In CI/CD script
docker compose -f virtualization/docker-compose.yml up -d
# Run tests
docker compose -f virtualization/docker-compose.yml down
```

### Customizing Resources

The `resources` directory is mounted as a volume, so you can modify the initial data:

1. Edit `virtualization/jira/resources/search.json`
2. Restart the container: `docker compose restart jira`
3. Or use the UI to reset to the new defaults

## License

MIT
