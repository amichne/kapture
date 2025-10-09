# Kapture Virtualization

Docker-based development and testing environment for Kapture.

## Quick Start

### Start All Services

```bash
docker compose up -d
```

This will start:
- **Jira Mock Server** on `http://localhost:8080`

### View Logs

```bash
# All services
docker compose logs -f

# Specific service
docker compose logs -f jira
```

### Stop All Services

```bash
docker compose down
```

## Services

### Jira Mock Server

A lightweight mock Jira server for testing Kapture without a real Jira instance.

- **URL**: http://localhost:8080
- **Admin UI**: http://localhost:8080
- **API**: http://localhost:8080/rest/api/3
- **Documentation**: [jira/README.md](jira/README.md)

**Features:**
- Jira REST API v3 endpoints
- Web UI for managing test data
- Create/edit/delete issues
- Status transitions
- Subtask support
- JQL search (basic)

### Configure Kapture

Point Kapture to the mock server:

```bash
# Use the sample config
git -k virtualization/jira/sample-config.json status

# Or update your config
export KAPTURE_CONFIG=virtualization/jira/sample-config.json
```

## Development

### Rebuild After Changes

```bash
docker compose up -d --build
```

### Access Container Shell

```bash
docker compose exec jira sh
```

### View Container Status

```bash
docker compose ps
```

## Testing Kapture Workflows

Once the services are running, try these Kapture commands:

```bash
# Configure Kapture to use the mock server
export KAPTURE_CONFIG=$PWD/virtualization/jira/sample-config.json

# Start work on an issue
git start TEST-2

# Create a subtask
git subtask TEST-2 "Implement feature"

# Make commits (auto-prefixed with ticket key)
git commit -m "Add validation"

# Create a PR and transition to Code Review
git review

# Merge and close
git merge --close-parent
```

## Troubleshooting

### Port 8080 Already in Use

Edit `docker-compose.yml` and change the host port:

```yaml
ports:
  - "3000:8080"  # Changed from 8080:8080
```

Then update the sample config to use `http://localhost:3000`.

### Container Won't Start

Check logs for errors:

```bash
docker compose logs jira
```

### Reset Everything

```bash
# Stop and remove containers
docker compose down

# Remove volumes (resets all data)
docker compose down -v

# Rebuild and start fresh
docker compose up -d --build
```

## CI/CD Integration

Use docker compose in your CI/CD pipelines:

```bash
# Start services
docker compose -f virtualization/docker-compose.yml up -d

# Wait for health check
sleep 5

# Run Kapture tests
./gradlew test

# Cleanup
docker compose -f virtualization/docker-compose.yml down
```

## Adding More Services

To add additional services (GitHub mock, etc.):

1. Create a new directory under `virtualization/`
2. Add a Dockerfile
3. Update `docker-compose.yml` with the new service
4. Document in this README

## License

MIT
