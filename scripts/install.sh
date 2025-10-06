#!/usr/bin/env bash
set -euo pipefail

# Kapture installation helper. Downloads the published Shadow JAR from GitHub releases,
# verifies its checksum when available, and installs a launcher script alongside it.

REPO="amichne/kapture"
INSTALL_DIR_DEFAULT="$HOME/.local/kapture"
BIN_DIR_DEFAULT="$HOME/.local/bin"
VERSION=""
FORCE=false

usage() {
  cat <<'EOF'
Usage: install.sh [options]

Options:
  --version <tag>     Install the specified release tag (e.g. v1.2.3). Defaults to the latest release.
  --install-dir <dir> Directory for the JAR (default: ~/.local/share/kapture).
  --bin-dir <dir>     Directory for the launcher script (default: ~/.local/bin).
  --repo <owner/name> Override the GitHub repository (default: amichne/kapture).
  --force             Overwrite existing files without prompting.
  -h, --help          Show this help message.

The installer fetches release assets named kapture.jar and kapture.jar.sha256. Ensure curl is available.
EOF
}

fail() {
  echo "[kapture] $*" >&2
  exit 1
}

expand_path() {
  local input="$1"
  if [[ "$input" == ~* ]]; then
    printf '%s\n' "${input/#\~/$HOME}"
  else
    printf '%s\n' "$input"
  fi
}

require_cmd() {
  local cmd="$1"
  command -v "$cmd" >/dev/null 2>&1 || fail "Required command '$cmd' not found in PATH"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version|-v)
      [[ $# -ge 2 ]] || fail "--version requires a value"
      VERSION="$2"
      shift 2
      ;;
    --install-dir)
      [[ $# -ge 2 ]] || fail "--install-dir requires a value"
      INSTALL_DIR_DEFAULT="$2"
      shift 2
      ;;
    --bin-dir)
      [[ $# -ge 2 ]] || fail "--bin-dir requires a value"
      BIN_DIR_DEFAULT="$2"
      shift 2
      ;;
    --repo)
      [[ $# -ge 2 ]] || fail "--repo requires a value"
      REPO="$2"
      shift 2
      ;;
    --force)
      FORCE=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      fail "Unknown option: $1"
      ;;
  esac
done

require_cmd curl

INSTALL_DIR=$(expand_path "$INSTALL_DIR_DEFAULT")
BIN_DIR=$(expand_path "$BIN_DIR_DEFAULT")

mkdir -p "$INSTALL_DIR"
mkdir -p "$BIN_DIR"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

JAR_URL="https://github.com/${REPO}/releases/latest/download/kapture.jar"
SUM_URL="https://github.com/${REPO}/releases/latest/download/kapture.jar.sha256"

if [[ -n "$VERSION" ]]; then
  JAR_URL="https://github.com/${REPO}/releases/download/${VERSION}/kapture.jar"
  SUM_URL="https://github.com/${REPO}/releases/download/${VERSION}/kapture.jar.sha256"
fi

JAR_PATH="$TMP_DIR/kapture.jar"
SUM_PATH="$TMP_DIR/kapture.jar.sha256"

echo "[kapture] Downloading kapture.jar from $JAR_URL"
if ! curl -fsSL "$JAR_URL" -o "$JAR_PATH"; then
  fail "Unable to download kapture.jar from $JAR_URL"
fi

VERIFY=false
echo "[kapture] Attempting to download checksum"
if curl -fsSL "$SUM_URL" -o "$SUM_PATH"; then
  VERIFY=true
fi

if [[ "$VERIFY" == true ]]; then
  echo "[kapture] Verifying checksum"
  EXPECTED=$(cut -d' ' -f1 "$SUM_PATH")
  if command -v sha256sum >/dev/null 2>&1; then
    ACTUAL=$(sha256sum "$JAR_PATH" | cut -d' ' -f1)
  elif command -v shasum >/dev/null 2>&1; then
    ACTUAL=$(shasum -a 256 "$JAR_PATH" | cut -d' ' -f1)
  else
    echo "[kapture] sha256sum/shasum not available; skipping verification"
    ACTUAL="$EXPECTED"
  fi
  [[ "$EXPECTED" == "$ACTUAL" ]] || fail "Checksum verification failed"
else
  echo "[kapture] Checksum not available; skipping verification"
fi

INSTALL_JAR="$INSTALL_DIR/kapture.jar"
if [[ -e "$INSTALL_JAR" && "$FORCE" != true ]]; then
  fail "${INSTALL_JAR} already exists. Re-run with --force to override."
fi

cp "$JAR_PATH" "$INSTALL_JAR"

echo "[kapture] Installed JAR to $INSTALL_JAR"

LAUNCHER_PATH="$BIN_DIR/kapture"
if [[ -e "$LAUNCHER_PATH" && "$FORCE" != true ]]; then
  fail "${LAUNCHER_PATH} already exists. Re-run with --force to override."
fi

cat > "$LAUNCHER_PATH" <<EOF
#!/usr/bin/env bash
set -euo pipefail
if [[ -z "\${REAL_GIT:-}" ]]; then
  export REAL_GIT="\$(command -v git)"
fi
exec java -jar "$INSTALL_JAR" "\$@"
EOF

chmod +x "$LAUNCHER_PATH"

echo "[kapture] Created launcher at $LAUNCHER_PATH"

echo "[kapture] Installation complete"
echo "[kapture] Ensure $BIN_DIR is on your PATH or link the launcher to your preferred git shim"
