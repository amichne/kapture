#!/usr/bin/env bash
set -euo pipefail

RUN_DEMO=true
DROP_SHELL=true
REAL_GIT_PATH=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-demo)
      RUN_DEMO=false
      shift
      ;;
    --no-shell)
      DROP_SHELL=false
      shift
      ;;
    --help|-h)
      cat <<'USAGE'
Usage: kapture-playground [options]

Options:
  --skip-demo    Prepare the environment but do not execute the scripted walkthrough.
  --no-shell     Exit after the walkthrough instead of dropping into an interactive shell.
  -h, --help     Show this help message.

Environment variables:
  PLAYGROUND_PARENT_KEY   Jira story/epic key used when creating the demo subtask (required).
  PLAYGROUND_SKIP_GH      When 'true', skip review/merge steps that require GitHub CLI.
  PLAYGROUND_WORKDIR      Working directory for the demo repository (default: /workspace/playground).
USAGE
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      exit 1
      ;;
  esac
  shift
done

demo_info() {
  echo "[playground] $*"
}

demo_cmd() {
  echo
  printf '$'
  for arg in "$@"; do
    printf ' %q' "$arg"
  done
  echo
  "$@"
}

demo_cmd_capture() {
  local __var="$1"
  shift
  local tmp
  tmp=$(mktemp)
  echo
  printf '$'
  for arg in "$@"; do
    printf ' %q' "$arg"
  done
  echo
  "$@" | tee "$tmp"
  printf -v "$__var" '%s' "$(cat "$tmp")"
  rm -f "$tmp"
}

prepare_git_shim() {
  local git_path
  git_path=$(command -v git)
  if [[ ! -x "${git_path}.real" ]]; then
    demo_info "Installing git shim for Kapture"
    mv "$git_path" "${git_path}.real"
    cat <<SH > "$git_path"
#!/usr/bin/env bash
export REAL_GIT="${git_path}.real"
exec /usr/local/bin/kapture "\$@"
SH
    chmod +x "$git_path"
  fi
  REAL_GIT_PATH="${git_path}.real"
}

write_config() {
  local config_dir="$HOME/.kapture"
  mkdir -p "$config_dir"
  cat <<CFG > "$config_dir/config.json"
{

  "branchPattern": "^(?<task>[A-Z]+-\\\\d+)/[a-z0-9._-]+$",
  "enforcement": {
    "branchPolicy": "WARN",
    "statusCheck": "WARN"
  },
  "statusRules": {
    "allowCommitWhen": ["IN_PROGRESS", "READY"],
    "allowPushWhen": ["READY", "IN_REVIEW"]
  },
  "external": {
    "type": "jiraCli",
    "environment": {
      "JIRA_USER_EMAIL": "${JIRA_USER_EMAIL:-user@example.com}",
      "JIRA_API_TOKEN": "${JIRA_API_TOKEN:-changeme}",
      "JIRA_SERVER": "${JIRA_SERVER:-http://jira:8080}"
    }
  },
  "trackingEnabled": false,
  "realGitHint": "${REAL_GIT_PATH}"
}
CFG
}

setup_demo_repo() {
  local workdir="${PLAYGROUND_WORKDIR:-/workspace/playground}"
  rm -rf "$workdir"
  mkdir -p "$workdir"
  cd "$workdir"
  demo_cmd git init
  demo_cmd git config user.email "demo@example.com"
  demo_cmd git config user.name "Demo User"
  demo_info "Seed example source file"
  printf "console.log('hello, kapture');\n" > app.js
  demo_cmd git add app.js
  demo_cmd git commit -m "Add sample app"
}

require_env() {
  if [[ -z "${PLAYGROUND_PARENT_KEY:-}" ]]; then
    echo "PLAYGROUND_PARENT_KEY must be set to an existing Jira story/epic key" >&2
    exit 1
  fi
}

run_walkthrough() {
  local subtask_output
  local subtask_key
  local skip_gh="${PLAYGROUND_SKIP_GH:-false}"

  demo_cmd git status

  demo_cmd_capture subtask_output git kapture subtask "$PLAYGROUND_PARENT_KEY" "Playground subtask"
  subtask_key=$(printf '%s\n' "$subtask_output" | sed -n 's/.*Created subtask: \([A-Z0-9-]*\).*/\1/p' | head -n1)
  if [[ -z "$subtask_key" ]]; then
    echo "Failed to parse subtask key from output" >&2
    exit 1
  fi

  demo_cmd git kapture branch "$subtask_key"

  demo_info "Modify source file"
  printf "console.log('hello, updated kapture');\n" >> app.js
  demo_cmd git status
  demo_cmd git add app.js
  demo_cmd git commit -m "Update sample app"

  if [[ "$skip_gh" != "true" ]]; then
    demo_cmd git kapture review
    demo_cmd git kapture merge
  else
    demo_info "Skipping review/merge (PLAYGROUND_SKIP_GH=true)"
  fi

  demo_cmd git log --oneline
}

prepare_git_shim
write_config
require_env
setup_demo_repo

demo_info "Environment ready. Jira server: ${JIRA_SERVER:-http://jira:8080}"

gh --version >/dev/null 2>&1 || demo_info "GitHub CLI not available; review/merge may fail"

if [[ "$RUN_DEMO" == true ]]; then
  if ! jira version >/dev/null 2>&1; then
    demo_info "jira-cli not authenticated or server unavailable; walkthrough may fail"
  fi
  if ! run_walkthrough; then
    demo_info "Walkthrough encountered errors"
  fi
else
  demo_info "Skipping scripted walkthrough (--skip-demo)"
fi

demo_info "Playground ready"

if [[ "$DROP_SHELL" == true ]]; then
  echo
  demo_info "Dropping into interactive shell (exit to leave container)"
  exec bash
fi
