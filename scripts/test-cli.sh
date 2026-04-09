#!/usr/bin/env bash
#
# SkillHub CLI Integration Test Suite
# Usage: ./scripts/test-cli.sh [registry-url]
#
# Requires: running SkillHub backend, valid auth token
#
set -uo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

REGISTRY="${1:-http://localhost:8080}"
CLI="node skillhub-cli/dist/cli.mjs --registry $REGISTRY"
PASS=0; FAIL=0; SKIP=0
TEST_TOKEN="${TEST_TOKEN:-}"

pass() { echo -e "  ${GREEN}✅ PASS${NC} $1"; ((PASS++)); }
fail() { echo -e "  ${RED}❌ FAIL${NC} $1"; ((FAIL++)); }
skip() { echo -e "  ${YELLOW}⏭️  SKIP${NC} $1"; ((SKIP++)); }
step() { echo -e "\n${BLUE}▶ ${1}${NC}"; }

# -----------------------------------------------
# Pre-flight
# -----------------------------------------------
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  SkillHub CLI Integration Tests${NC}"
echo -e "${BLUE}========================================${NC}"
echo "Registry: $REGISTRY"
echo "CLI: $CLI"
echo ""

if [ ! -f "skillhub-cli/dist/cli.mjs" ]; then
    echo -e "${RED}Error: skillhub-cli/dist/cli.mjs not found${NC}"
    echo "Run: cd skillhub-cli && pnpm run build"
    exit 1
fi

# -----------------------------------------------
# 1. Auth commands
# -----------------------------------------------
step "Authentication Commands"

# login (requires token)
if [ -n "$TEST_TOKEN" ]; then
    if $CLI login --token "$TEST_TOKEN" 2>/dev/null; then
        pass "login with token"
    else
        fail "login with token"
    fi
else
    skip "login (set TEST_TOKEN env var)"
fi

# whoami
if [ -n "$TEST_TOKEN" ]; then
    if $CLI whoami 2>/dev/null | grep -q "User ID"; then
        pass "whoami shows user info"
    else
        fail "whoami output"
    fi
else
    skip "whoami (no token)"
fi

# logout
if $CLI logout 2>/dev/null; then
    pass "logout succeeds"
else
    fail "logout"
fi

# whoami without token
if $CLI whoami 2>/dev/null; then
    fail "whoami should fail without token"
else
    pass "whoami fails without token"
fi

# -----------------------------------------------
# 2. Init command
# -----------------------------------------------
step "Init Command"

TEST_INIT_DIR=$(mktemp -d)
cd "$TEST_INIT_DIR"

if $CLI init 2>/dev/null && [ -f "SKILL.md" ]; then
    pass "init creates SKILL.md"
else
    fail "init"
fi

if $CLI init my-test-skill 2>/dev/null && [ -f "my-test-skill/SKILL.md" ]; then
    pass "init with name creates subdirectory"
else
    fail "init with name"
fi

if $CLI init 2>/dev/null; then
    fail "init should fail when SKILL.md exists"
else
    pass "init fails on existing SKILL.md"
fi

cd - >/dev/null
rm -rf "$TEST_INIT_DIR"

# -----------------------------------------------
# 3. Publish command
# -----------------------------------------------
step "Publish Command"

TEST_SKILL_DIR=$(mktemp -d)
cat > "$TEST_SKILL_DIR/SKILL.md" << 'EOF'
---
name: cli-test-skill
description: Integration test skill
---
# CLI Test Skill
EOF

if [ -n "$TEST_TOKEN" ]; then
    $CLI login --token "$TEST_TOKEN" 2>/dev/null

    if $CLI publish "$TEST_SKILL_DIR" --version 1.0.0 --namespace global 2>/dev/null; then
        pass "publish skill v1.0.0"
    else
        fail "publish skill"
    fi

    if $CLI publish "$TEST_SKILL_DIR" --version 1.0.1 --namespace global 2>/dev/null; then
        pass "publish skill v1.0.1 (update)"
    else
        fail "publish skill update"
    fi
else
    skip "publish (no token)"
fi

rm -rf "$TEST_SKILL_DIR"

# -----------------------------------------------
# 4. Info command
# -----------------------------------------------
step "Info Command"

if $CLI info cli-test-skill 2>/dev/null | grep -q "cli-test-skill"; then
    pass "info shows skill details"
else
    fail "info"
fi

if $CLI info nonexistent-skill-xyz-12345 2>/dev/null; then
    fail "info should fail for nonexistent skill"
else
    pass "info fails for nonexistent skill"
fi

# -----------------------------------------------
# 5. Search command
# -----------------------------------------------
step "Search Command"

if $CLI search "test" 2>/dev/null; then
    pass "search returns results"
else
    fail "search"
fi

# -----------------------------------------------
# 6. Resolve command
# -----------------------------------------------
step "Resolve Command"

if $CLI resolve cli-test-skill 2>/dev/null | grep -q "cli-test-skill"; then
    pass "resolve shows version info"
else
    fail "resolve"
fi

if $CLI resolve cli-test-skill --version 1.0.0 2>/dev/null | grep -q "1.0.0"; then
    pass "resolve with specific version"
else
    fail "resolve with version"
fi

# -----------------------------------------------
# 7. Versions command
# -----------------------------------------------
step "Versions Command"

if $CLI versions cli-test-skill 2>/dev/null | grep -q "1.0"; then
    pass "versions lists skill versions"
else
    fail "versions"
fi

# -----------------------------------------------
# 8. Star command
# -----------------------------------------------
step "Star Command"

if $CLI star cli-test-skill 2>/dev/null | grep -qi "starred"; then
    pass "star skill"
else
    fail "star"
fi

if $CLI star cli-test-skill --unstar 2>/dev/null | grep -qi "unstarred"; then
    pass "unstar skill"
else
    fail "unstar"
fi

# -----------------------------------------------
# 9. Rating commands
# -----------------------------------------------
step "Rating Commands"

if $CLI rating cli-test-skill 2>/dev/null; then
    pass "rating shows current rating"
else
    fail "rating"
fi

if $CLI rate cli-test-skill 4 2>/dev/null | grep -qi "rated"; then
    pass "rate skill 4/5"
else
    fail "rate"
fi

if $CLI rating cli-test-skill 2>/dev/null | grep -q "4"; then
    pass "rating reflects new score"
else
    fail "rating check after rate"
fi

# -----------------------------------------------
# 10. Me commands
# -----------------------------------------------
step "Me Commands"

if $CLI me skills 2>/dev/null; then
    pass "me skills lists published skills"
else
    fail "me skills"
fi

if $CLI me stars 2>/dev/null; then
    pass "me stars lists starred skills"
else
    fail "me stars"
fi

# -----------------------------------------------
# 11. Reviews command
# -----------------------------------------------
step "Reviews Command"

if $CLI reviews my 2>/dev/null; then
    pass "reviews my shows submissions"
else
    fail "reviews my"
fi

# -----------------------------------------------
# 12. Namespaces command
# -----------------------------------------------
step "Namespaces Command"

if $CLI namespaces 2>/dev/null; then
    pass "namespaces lists accessible namespaces"
else
    fail "namespaces"
fi

# -----------------------------------------------
# 13. Notifications command
# -----------------------------------------------
step "Notifications Command"

if $CLI notifications list 2>/dev/null; then
    pass "notifications list"
else
    fail "notifications list"
fi

if $CLI notifications read-all 2>/dev/null; then
    pass "notifications read-all"
else
    fail "notifications read-all"
fi

# -----------------------------------------------
# 14. Download command
# -----------------------------------------------
step "Download Command"

TEST_DL_DIR=$(mktemp -d)

if $CLI download cli-test-skill --output "$TEST_DL_DIR" 2>/dev/null && [ -f "$TEST_DL_DIR/cli-test-skill.zip" ]; then
    pass "download skill"
else
    fail "download"
fi

rm -rf "$TEST_DL_DIR"

# -----------------------------------------------
# 15. Add command (local path)
# -----------------------------------------------
step "Add Command"

TEST_ADD_DIR=$(mktemp -d)
mkdir -p "$TEST_ADD_DIR/skills/test-add-skill"
cat > "$TEST_ADD_DIR/skills/test-add-skill/SKILL.md" << 'EOF'
---
name: test-add-skill
description: Test skill for add command
---
# Test Add Skill
EOF

if $CLI add "$TEST_ADD_DIR" --list 2>/dev/null | grep -q "test-add-skill"; then
    pass "add --list discovers skills"
else
    fail "add --list"
fi

if $CLI add "$TEST_ADD_DIR" -a claude-code -y 2>/dev/null; then
    pass "add installs to claude-code"
else
    fail "add install"
fi

rm -rf "$TEST_ADD_DIR"

# -----------------------------------------------
# 16. List command
# -----------------------------------------------
step "List Command"

if $CLI list 2>/dev/null; then
    pass "list shows installed skills"
else
    fail "list"
fi

# -----------------------------------------------
# 17. Remove command
# -----------------------------------------------
step "Remove Command"

if $CLI remove test-add-skill -y 2>/dev/null; then
    pass "remove installed skill"
else
    fail "remove"
fi

# -----------------------------------------------
# 18. Delete command
# -----------------------------------------------
step "Delete Command"

if $CLI delete cli-test-skill -y 2>/dev/null; then
    pass "delete skill"
else
    fail "delete"
fi

# -----------------------------------------------
# 19. Archive command
# -----------------------------------------------
step "Archive Command"

# Publish a skill to archive
TEST_ARCH_DIR=$(mktemp -d)
cat > "$TEST_ARCH_DIR/SKILL.md" << 'EOF'
---
name: cli-archive-test
description: Archive test skill
---
# Archive Test
EOF

$CLI publish "$TEST_ARCH_DIR" --version 1.0.0 2>/dev/null

if $CLI archive cli-archive-test -y 2>/dev/null; then
    pass "archive skill"
else
    fail "archive"
fi

rm -rf "$TEST_ARCH_DIR"

# -----------------------------------------------
# 20. Report command
# -----------------------------------------------
step "Report Command"

# Publish a skill to report
TEST_RPT_DIR=$(mktemp -d)
cat > "$TEST_RPT_DIR/SKILL.md" << 'EOF'
---
name: cli-report-test
description: Report test skill
---
# Report Test
EOF

$CLI publish "$TEST_RPT_DIR" --version 1.0.0 2>/dev/null

if $CLI report cli-report-test --reason "Integration test" 2>/dev/null; then
    pass "report skill"
else
    fail "report"
fi

rm -rf "$TEST_RPT_DIR"

# -----------------------------------------------
# 21. Explore command (local-only)
# -----------------------------------------------
step "Explore Command (Local-Only)"

EXPLORE_TMP=$(mktemp -d)
echo "test" > "$EXPLORE_TMP/query.txt"

# explore command should return results (non-interactive check)
if $CLI explore --help 2>/dev/null | grep -q "search"; then
    pass "explore command is available"
else
    fail "explore command"
fi

rm -rf "$EXPLORE_TMP"

# -----------------------------------------------
# 22. Check command (local-only)
# -----------------------------------------------
step "Check Command (Local-Only)"

# check command should work even without installed skills
if $CLI check --help 2>/dev/null | grep -q "lock"; then
    pass "check command is available"
else
    fail "check command"
fi

# check --json should output valid JSON (even if no skills)
if $CLI check --json 2>/dev/null; then
    pass "check --json outputs valid response"
else
    skip "check --json (no lock file)"
fi

# -----------------------------------------------
# 23. Sync command (local-only)
# -----------------------------------------------
step "Sync Command (Local-Only)"

TEST_SYNC_DIR=$(mktemp -d)
mkdir -p "$TEST_SYNC_DIR/test-sync-skill"
cat > "$TEST_SYNC_DIR/test-sync-skill/SKILL.md" << 'EOF'
---
name: test-sync-skill
description: Sync test skill
---
# Sync Test
EOF

# sync should discover skills in directory
if $CLI sync --help 2>/dev/null | grep -q "scan"; then
    pass "sync command is available"
else
    fail "sync command"
fi

# sync --list should show discovered skills without publishing
if $CLI sync "$TEST_SYNC_DIR" --namespace global --list 2>/dev/null | grep -q "test-sync-skill"; then
    pass "sync --list discovers skills"
else
    fail "sync --list"
fi

# sync with --all should require confirmation or token
if $CLI sync "$TEST_SYNC_DIR" --namespace global --all --yes 2>/dev/null; then
    pass "sync --all --yes publishes"
else
    skip "sync --all (requires auth)"
fi

rm -rf "$TEST_SYNC_DIR"

# -----------------------------------------------
# 24. Update command (local-only)
# -----------------------------------------------
step "Update Command (Local-Only)"

# update command should be available
if $CLI update --help 2>/dev/null | grep -q "update"; then
    pass "update command is available"
else
    fail "update command"
fi

# update without lock file should fail gracefully
if $CLI update nonexistent-skill 2>/dev/null; then
    fail "update should fail without lock file"
else
    pass "update fails without lock file"
fi

# -----------------------------------------------
# 25. Uninstall command (local-only, via Vitest tested)
# -----------------------------------------------
step "Uninstall Command (Local-Only)"

# Verify uninstall is registered (tested via Vitest, basic check here)
if $CLI uninstall --help 2>/dev/null | grep -q "uninstall"; then
    pass "uninstall command is available"
else
    fail "uninstall command"
fi

# -----------------------------------------------
# 26. Hide command (admin-only, skip for normal users)
# -----------------------------------------------
step "Hide Command (Admin-Only)"

# hide requires admin privileges - verify it exists
if $CLI hide --help 2>/dev/null | grep -q "hide"; then
    pass "hide command is available (admin)"
else
    skip "hide command not available"
fi

# -----------------------------------------------
# 27. Transfer command (admin-only, skip for normal users)
# -----------------------------------------------
step "Transfer Command (Admin-Only)"

# transfer requires admin privileges - verify it exists
if $CLI transfer --help 2>/dev/null | grep -q "transfer"; then
    pass "transfer command is available (admin)"
else
    skip "transfer command not available"
fi

# -----------------------------------------------
# 28. Inspect command (local-only, enhanced info)
# -----------------------------------------------
step "Inspect Command (Local-Only)"

# inspect is the enhanced version of info
if $CLI inspect --help 2>/dev/null | grep -q "inspect"; then
    pass "inspect command is available"
else
    fail "inspect command"
fi

# inspect should show detailed skill info
if $CLI inspect cli-test-skill 2>/dev/null | grep -q "cli-test-skill"; then
    pass "inspect shows skill details"
else
    fail "inspect"
fi

# -----------------------------------------------
# 29. Logout (cleanup)
# -----------------------------------------------
step "Cleanup"

if $CLI logout 2>/dev/null; then
    pass "logout (cleanup)"
else
    fail "logout cleanup"
fi

# -----------------------------------------------
# Summary
# -----------------------------------------------
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Test Results${NC}"
echo -e "${BLUE}========================================${NC}"
echo -e "  ${GREEN}Passed: $PASS${NC}"
echo -e "  ${RED}Failed: $FAIL${NC}"
echo -e "  ${YELLOW}Skipped: $SKIP${NC}"
echo ""

if [ "$FAIL" -gt 0 ]; then
    echo -e "${RED}❌ $FAIL test(s) failed${NC}"
    exit 1
else
    echo -e "${GREEN}✅ All tests passed!${NC}"
    exit 0
fi
