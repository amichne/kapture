# Docker Setup for Kapture Demo

Quick guide to run the Kapture demo environment with Docker.

## 🚀 One-Command Start

```bash
cd virtualization
docker compose up -d
```

This starts the mock Jira server at **http://localhost:8080**

## 📊 Access the Admin UI

Open your browser to [http://localhost:8080](http://localhost:8080) to:
- View all mock issues
- Create new issues and subtasks
- Edit issue status and details
- Reset to default data

## ⚙️ Configure Kapture

Point Kapture to the mock server using the sample config:

```bash
# From the repository root
git -k virtualization/jira/sample-config.json status
```

Or set an environment variable:

```bash
export KAPTURE_CONFIG=$PWD/virtualization/jira/sample-config.json
git status
```

## 🎯 Try It Out

```bash
# 1. Start work on an issue
git start TEST-2

# 2. Create a subtask
git subtask TEST-2 "Add password validation"

# 3. Make commits (auto-prefixed with ticket key)
git commit -m "Implement validation logic"

# 4. Create a PR
git review

# 5. Merge and close
git merge --close-parent
```

## 🛑 Stop the Server

```bash
cd virtualization
docker compose down
```

## 📝 View Logs

```bash
cd virtualization
docker compose logs -f jira
```

## 🔧 Troubleshooting

### Port 8080 already in use?

Edit `virtualization/docker-compose.yml` and change the port:

```yaml
ports:
  - "3000:8080"  # Changed from 8080:8080
```

Then use `http://localhost:3000` instead.

### Container won't start?

Check the logs:

```bash
docker compose logs jira
```

### Reset everything?

```bash
cd virtualization
docker compose down -v
docker compose up -d --build
```

## 📚 More Information

- [Virtualization README](virtualization/README.md) - Full Docker setup documentation
- [Jira Mock Server README](virtualization/jira/README.md) - Mock server details
- [Main README](README.md) - Kapture overview and features

## ✅ What You Get

- ✅ No Jira instance needed
- ✅ No Node.js installation needed
- ✅ Consistent environment across machines
- ✅ Easy to share with team members
- ✅ Perfect for demos and testing
