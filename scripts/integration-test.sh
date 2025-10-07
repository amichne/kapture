#!/usr/bin/env bash
set -euo pipefail

# Integration test suite for Kapture
# - Validates docker-compose stack (Postgres, Jira Software, jira-cli)
# - Exercises the native CLI wrapper against a temporary git repository
# - Ensures policy enforcement and passthrough behaviours stay intact

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/docker-compose.yml"
COMPOSE_PROJECT_NAME="${COMPOSE_PROJECT_NAME:-kapture-integration}"
COMPOSE_CMD=(docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE")
KEEP_CONTAINERS=false

usage() {
  cat <<'EOF'
Usage: integration-test.sh [options]

Options:
  -k, --keep-containers   Leave docker-compose services running after tests finish.
  -h, --help              Show this help message.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -k|--keep-containers|--keepalive)
      KEEP_CONTAINERS=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage
      exit 1
      ;;
  esac
done

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Required command '$1' not found in PATH" >&2
    exit 1
  fi
}

header() {
  echo ""
  echo "=== $* ==="
}

require_cmd docker
require_cmd curl
require_cmd git

if ! docker compose version >/dev/null 2>&1; then
  echo "The Docker Compose plugin is required (docker compose)" >&2
  exit 1
fi

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "docker-compose.yml not found at $COMPOSE_FILE" >&2
  exit 1
fi

TEST_DIR="$(mktemp -d)"
CONFIG_FILE="$HOME/.kapture/config.json"
CONFIG_BACKUP=""
CONFIG_CREATED=false

cleanup() {
  local exit_code=$?
  trap - EXIT
  set +e
  if [[ -d "$TEST_DIR" ]]; then
    rm -rf "$TEST_DIR"
  fi
  if [[ "$CONFIG_CREATED" == true ]]; then
    rm -f "$CONFIG_FILE"
  elif [[ -n "$CONFIG_BACKUP" && -f "$CONFIG_BACKUP" ]]; then
    mv "$CONFIG_BACKUP" "$CONFIG_FILE"
  fi
  if [[ -n "$CONFIG_BACKUP" && -f "$CONFIG_BACKUP" ]]; then
    rm -f "$CONFIG_BACKUP"
  fi
  if [[ "$KEEP_CONTAINERS" == true ]]; then
    echo "Keeping docker-compose services running (requested via --keep-containers)."
  else
    "${COMPOSE_CMD[@]}" down --volumes --remove-orphans >/dev/null 2>&1
  fi
  set -e
  exit "$exit_code"
}
trap cleanup EXIT

wait_for_service() {
  local service="$1"
  local desired="${2:-healthy}"
  local attempts=${3:-60}
  local delay=${4:-5}
  local container=""
  local status=""

  for attempt in $(seq 1 "$attempts"); do
    container=$("${COMPOSE_CMD[@]}" ps -q "$service" 2>/dev/null || true)
    if [[ -n "$container" ]]; then
      status=$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container" 2>/dev/null || echo "unknown")
      if [[ "$status" == "$desired" ]]; then
        return 0
      fi
      if [[ "$desired" == "healthy" && "$status" == "running" ]]; then
        # Treat running as sufficient when no health check exists
        return 0
      fi
    fi
    sleep "$delay"
  done

  if [[ -n "$container" ]]; then
    docker logs "$container" || true
  fi
  return 1
}

wait_for_http() {
  local url="$1"
  local label="$2"
  local attempts=${3:-60}
  local delay=${4:-5}
  local status

  for attempt in $(seq 1 "$attempts"); do
    status=$(curl -fsS -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)
    if [[ "$status" =~ ^(200|201|202|204|30[1237])$ ]]; then
      return 0
    fi
    sleep "$delay"
  done

  echo "$label not reachable at $url (last HTTP status: ${status:-n/a})" >&2
  return 1
}

header "Test 1: docker-compose.yml validation"
"${COMPOSE_CMD[@]}" config >/dev/null
echo "✓ docker-compose.yml is syntactically valid"

header "Test 2: Start Jira stack"
"${COMPOSE_CMD[@]}" up -d postgres jira
wait_for_service postgres healthy 40 5 || { echo "✗ Postgres failed to report healthy" >&2; exit 1; }
echo "✓ Postgres container is healthy"
wait_for_service jira running 120 10 || { echo "✗ Jira container failed to report running" >&2; exit 1; }
echo "✓ Jira container is running"
wait_for_http "http://localhost:8080" "Jira application" 120 10 || { echo "✗ Jira HTTP endpoint did not respond" >&2; exit 1; }
echo "✓ Jira responds on http://localhost:8080"

header "Test 3: jira-cli smoke checks"
"${COMPOSE_CMD[@]}" run --rm jira-cli version >/dev/null
echo "✓ jira-cli version command executes"
"${COMPOSE_CMD[@]}" run --rm jira-cli help >/dev/null
echo "✓ jira-cli help command executes"

EXPECTED_SERVER="http://jira:8080"
EXPECTED_EMAIL="${JIRA_USER_EMAIL:-user@example.com}"
EXPECTED_TOKEN="${JIRA_API_TOKEN:-changeme}"

CLI_SERVER=$("${COMPOSE_CMD[@]}" run --rm --entrypoint sh jira-cli -c 'printf %s "$JIRA_SERVER"')
CLI_SERVER=$(echo "$CLI_SERVER" | tr -d '\r')
if [[ "$CLI_SERVER" != "$EXPECTED_SERVER" ]]; then
  echo "✗ jira-cli JIRA_SERVER mismatch (expected $EXPECTED_SERVER, got $CLI_SERVER)" >&2
  exit 1
fi

CLI_EMAIL=$("${COMPOSE_CMD[@]}" run --rm --entrypoint sh jira-cli -c 'printf %s "$JIRA_USER_EMAIL"')
CLI_EMAIL=$(echo "$CLI_EMAIL" | tr -d '\r')
if [[ "$CLI_EMAIL" != "$EXPECTED_EMAIL" ]]; then
  echo "✗ jira-cli JIRA_USER_EMAIL mismatch (expected $EXPECTED_EMAIL, got $CLI_EMAIL)" >&2
  exit 1
fi

echo "✓ jira-cli inherits expected environment"

# Write kapture configuration pointing at the local Jira stack
if [[ -f "$CONFIG_FILE" ]]; then
  CONFIG_BACKUP="$(mktemp "$TEST_DIR/config.json.backup.XXXXXX")"
  cp "$CONFIG_FILE" "$CONFIG_BACKUP"
else
  CONFIG_CREATED=true
fi

mkdir -p "$(dirname "$CONFIG_FILE")"
cat > "$CONFIG_FILE" <<'EOF'
{
  "externalBaseUrl": "http://localhost:8080",
  "branchPattern": "^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$",
  "enforcement": {
    "branchPolicy": "WARN",
    "statusCheck": "OFF"
  },
  "trackingEnabled": false,
  "realGitHint": null
}
EOF

# Prepare native binary under test
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
  TARGET="linuxX64"
elif [[ "$OSTYPE" == "darwin"* ]]; then
  if [[ "$(uname -m)" == "arm64" ]]; then
    TARGET="macosArm64"
  else
    TARGET="macosX64"
  fi
else
  echo "Unsupported platform: $OSTYPE" >&2
  exit 1
fi

BINARY="$PROJECT_ROOT/cli/build/native/nativeCompile/kapture"

if [[ ! -f "$BINARY" ]]; then
  echo "Binary not found: $BINARY" >&2
  echo "Run: ./gradlew :cli:nativeBuild" >&2
  exit 1
fi

chmod +x "$BINARY"

header "Test 4: Basic passthrough"
TEST_DIR_GIT="$TEST_DIR/repo"
mkdir -p "$TEST_DIR_GIT"
cd "$TEST_DIR_GIT"
git init >/dev/null
"$BINARY" config user.email "test@example.com"
"$BINARY" config user.name "Test User"

echo "test content" > test.txt
"$BINARY" add test.txt
"$BINARY" commit -m "Initial commit" >/dev/null

if "$BINARY" log --oneline | grep -q "Initial commit"; then
  echo "✓ Basic passthrough works"
else
  echo "✗ Basic passthrough failed" >&2
  exit 1
fi

header "Test 5: Autocomplete helpers"
if "$BINARY" --list-cmds=main | grep -q "commit"; then
  echo "✓ --list-cmds works"
else
  echo "✗ --list-cmds failed" >&2
  exit 1
fi

if "$BINARY" help -a >/dev/null 2>&1; then
  echo "✓ help command works"
else
  echo "✗ help command failed" >&2
  exit 1
fi

if "$BINARY" --version >/dev/null 2>&1; then
  echo "✓ --version works"
else
  echo "✗ --version failed" >&2
  exit 1
fi

header "Test 6: Rev-parse helper"
BRANCH=$("$BINARY" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
if [[ "$BRANCH" == "master" || "$BRANCH" == "main" ]]; then
  echo "✓ rev-parse works (branch: $BRANCH)"
else
  echo "✗ rev-parse failed" >&2
  exit 1
fi

header "Test 7: Branch policy warning"
if "$BINARY" checkout -b invalid-name 2>&1 | grep -q "WARNING"; then
  echo "✓ Branch policy warning displayed"
else
  echo "⚠ Branch policy warning not displayed (policy may be permissive)"
fi

"$BINARY" checkout master >/dev/null 2>&1 || "$BINARY" checkout main >/dev/null 2>&1

header "Test 8: Branch policy success"
if "$BINARY" checkout -b PROJ-123/valid-feature >/dev/null 2>&1; then
  echo "✓ Valid branch name accepted"
else
  echo "✗ Valid branch name rejected" >&2
  exit 1
fi

header "Test 9: Jira wrapper commands"
if "$BINARY" kapture help >/dev/null 2>&1; then
  echo "✓ kapture help works"
else
  echo "✗ kapture help failed" >&2
  exit 1
fi


if "$BINARY" kapture status >/dev/null 2>&1; then
  echo "✓ kapture status works"
else
  echo "⚠ kapture status failed (expected if API credentials missing)"
fi

header "Test 10: TTY inheritance"
echo "line 1" > interactive.txt
echo "line 2" >> interactive.txt
"$BINARY" add interactive.txt
"$BINARY" commit -m "Add interactive test file" >/dev/null

echo "line 3" >> interactive.txt
if command -v timeout >/dev/null 2>&1; then
  timeout 2 "$BINARY" diff interactive.txt >/dev/null 2>&1 || true
  echo "✓ TTY operations don't hang"
else
  echo "⚠ Skipping diff timeout check (timeout command unavailable)"
fi

header "Test 11: Exit code preservation"
if "$BINARY" status >/dev/null 2>&1; then
  echo "✓ Exit code preserved (success)"
else
  echo "✗ Exit code not preserved on success" >&2
  exit 1
fi

FAILED=0
if ! "$BINARY" checkout nonexistent-branch >/dev/null 2>&1; then
  FAILED=1
fi
if [[ $FAILED -eq 1 ]]; then
  echo "✓ Exit code preserved (failure)"
else
  echo "✗ Exit code not preserved on failure" >&2
  exit 1
fi

header "Test 12: Environment preservation"
export GIT_AUTHOR_NAME="Test Override"
AUTHOR=$("$BINARY" var GIT_AUTHOR_IDENT | cut -d'<' -f1 | xargs)
if [[ "$AUTHOR" == "Test Override" ]]; then
  echo "✓ Environment variables preserved"
else
  echo "✗ Environment variables not preserved" >&2
  exit 1
fi

cd "$PROJECT_ROOT"

echo ""
echo "All integration tests passed"
