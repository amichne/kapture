#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$PROJECT_ROOT/virtualization/stack/docker-compose.yml"
PLAYGROUND_SERVICE="playground"

if [[ ! -f "$COMPOSE_FILE" ]]; then
  echo "Playground compose file not found: $COMPOSE_FILE" >&2
  exit 1
fi

echo "[playground] Building playground image"
# shellcheck disable=SC2097
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-kapture-playground} \
  docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" build "$PLAYGROUND_SERVICE"

# shellcheck disable=SC2097
echo "[playground] Launching playground container"
COMPOSE_PROJECT_NAME=${COMPOSE_PROJECT_NAME:-kapture-playground} \
  docker compose -p "$COMPOSE_PROJECT_NAME" -f "$COMPOSE_FILE" run --rm "$PLAYGROUND_SERVICE" "$@"
