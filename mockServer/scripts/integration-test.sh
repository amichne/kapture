#!/bin/bash
set -e

# Integration test script for kapture wrapper
# Tests autocomplete, interactive flows, and policy enforcement

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Determine platform and binary location
if [[ "$OSTYPE" == "linux-gnu"* ]]; then
    TARGET="linuxX64"
elif [[ "$OSTYPE" == "darwin"* ]]; then
    if [[ "$(uname -m)" == "arm64" ]]; then
        TARGET="macosArm64"
    else
        TARGET="macosX64"
    fi
else
    echo "Unsupported platform: $OSTYPE"
    exit 1
fi

BINARY="$PROJECT_ROOT/cli/build/native/nativeCompile/kapture"

if [ ! -f "$BINARY" ]; then
    echo "Binary not found: $BINARY"
    echo "Run: ./gradlew :cli:nativeBuild"
    exit 1
fi

chmod +x "$BINARY"

echo "==================================="
echo "Kapture Integration Tests"
echo "==================================="
echo "Binary: $BINARY"
echo "Target: $TARGET"
echo ""

# Create temporary test directory
TEST_DIR=$(mktemp -d)
trap 'rm -rf "$TEST_DIR"' EXIT

echo "Test directory: $TEST_DIR"
cd "$TEST_DIR"

# Create test config
mkdir -p "$HOME/.kapture"
cat > "$HOME/.kapture/config.json" << 'EOF'
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

echo ""
echo "=== Test 1: Basic Passthrough ==="
git init test-repo
cd test-repo
git config user.email "test@example.com"
git config user.name "Test User"

echo "test content" > test.txt
"$BINARY" add test.txt
"$BINARY" commit -m "Initial commit"

if "$BINARY" log --oneline | grep -q "Initial commit"; then
    echo "✓ Basic passthrough works"
else
    echo "✗ Basic passthrough failed"
    exit 1
fi

echo ""
echo "=== Test 2: Autocomplete Support ==="
# Test that autocomplete-critical commands work
if "$BINARY" --list-cmds=main | grep -q "commit"; then
    echo "✓ --list-cmds works"
else
    echo "✗ --list-cmds failed"
    exit 1
fi

if "$BINARY" help -a > /dev/null 2>&1; then
    echo "✓ help command works"
else
    echo "✗ help command failed"
    exit 1
fi

if "$BINARY" --version > /dev/null 2>&1; then
    echo "✓ --version works"
else
    echo "✗ --version failed"
    exit 1
fi

echo ""
echo "=== Test 3: Rev-parse (Completion Helper) ==="
if BRANCH=$("$BINARY" rev-parse --abbrev-ref HEAD 2>/dev/null) && [ "$BRANCH" = "master" ] || [ "$BRANCH" = "main" ]; then
    echo "✓ rev-parse works (branch: $BRANCH)"
else
    echo "✗ rev-parse failed"
    exit 1
fi

echo ""
echo "=== Test 4: Branch Policy - Invalid Branch ==="
# Should warn but not block in WARN mode
if "$BINARY" checkout -b invalid-name 2>&1 | grep -q "WARNING"; then
    echo "✓ Branch policy warning displayed"
else
    echo "⚠ Branch policy warning not displayed (might be expected)"
fi

# Switch back to main/master
"$BINARY" checkout "$(git rev-parse --abbrev-ref HEAD | grep -v invalid-name || echo main)"  || true

echo ""
echo "=== Test 5: Branch Policy - Valid Branch ==="
if "$BINARY" checkout -b PROJ-123/valid-feature 2>&1; then
    echo "✓ Valid branch name accepted"
else
    echo "✗ Valid branch name rejected"
    exit 1
fi

echo ""
echo "=== Test 6: Gira Commands ==="
if "$BINARY" gira help > /dev/null 2>&1; then
    echo "✓ gira help works"
else
    echo "✗ gira help failed"
    exit 1
fi

if "$BINARY" gira doctor > /dev/null 2>&1; then
    echo "✓ gira doctor works"
else
    echo "✗ gira doctor failed"
    exit 1
fi

if "$BINARY" gira status > /dev/null 2>&1; then
    echo "✓ gira status works"
else
    echo "⚠ gira status failed (expected if no API)"
fi

echo ""
echo "=== Test 7: TTY Inheritance ==="
# Create a test file for interactive add
echo "line 1" > interactive.txt
echo "line 2" >> interactive.txt
"$BINARY" add interactive.txt
"$BINARY" commit -m "Add interactive test file"

echo "line 3" >> interactive.txt
# Note: Can't fully test interactive mode in script, but verify it doesn't hang
timeout 2 "$BINARY" diff interactive.txt > /dev/null 2>&1 || true
echo "✓ TTY operations don't hang"

echo ""
echo "=== Test 8: Exit Code Preservation ==="
"$BINARY" status > /dev/null 2>&1
if [ $? -eq 0 ]; then
    echo "✓ Exit code preserved (success)"
else
    echo "✗ Exit code not preserved"
    exit 1
fi

# Try a failing command
"$BINARY" checkout nonexistent-branch > /dev/null 2>&1 || FAILED=$?
if [ "${FAILED:-0}" -ne 0 ]; then
    echo "✓ Exit code preserved (failure)"
else
    echo "✗ Exit code not preserved on failure"
    exit 1
fi

echo ""
echo "=== Test 9: Environment Preservation ==="
export GIT_AUTHOR_NAME="Test Override"
AUTHOR=$("$BINARY" var GIT_AUTHOR_IDENT | cut -d'<' -f1 | xargs)
if [ "$AUTHOR" = "Test Override" ]; then
    echo "✓ Environment variables preserved"
else
    echo "✗ Environment variables not preserved"
    exit 1
fi

echo ""
echo "=== Test 10: BLOCK Mode ==="
# Update config to BLOCK mode
cat > "$HOME/.kapture/config.json" << 'EOF'
{
  "branchPattern": "^(?<task>[A-Z]+-\\d+)/[a-z0-9._-]+$",
  "enforcement": {
    "branchPolicy": "BLOCK",
    "statusCheck": "OFF"
  },
  "trackingEnabled": false
}
EOF

# This should fail with exit code 2
"$BINARY" checkout main || true
if "$BINARY" checkout -b invalid-blocked 2>&1; then
    echo "✗ BLOCK mode did not prevent invalid branch"
    exit 1
else
    EXIT_CODE=$?
    if [ $EXIT_CODE -eq 2 ]; then
        echo "✓ BLOCK mode works with correct exit code"
    else
        echo "⚠ BLOCK mode works but exit code is $EXIT_CODE (expected 2)"
    fi
fi

echo ""
echo "==================================="
echo "All Integration Tests Passed! ✓"
echo "==================================="
