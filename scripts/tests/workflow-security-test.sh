#!/usr/bin/env bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SECURITY_WORKFLOW="$REPO_ROOT/.github/workflows/security.yml"
PR_SCRIPTS_WORKFLOW="$REPO_ROOT/.github/workflows/pr-scripts.yml"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

assert_pr_workflow_hardened() {
  local workflow="$1"
  grep -Eq '^permissions:[[:space:]]*$' "$workflow" \
    || fail "$workflow must declare top-level permissions"
  grep -Eq '^[[:space:]]+contents:[[:space:]]+read[[:space:]]*$' "$workflow" \
    || fail "$workflow GITHUB_TOKEN permissions must include contents: read"
  grep -Fq 'persist-credentials: false' "$workflow" \
    || fail "$workflow checkout steps must not persist credentials"
}

[[ -f "$SECURITY_WORKFLOW" ]] || fail ".github/workflows/security.yml is required"

assert_pr_workflow_hardened "$REPO_ROOT/.github/workflows/pr-cli.yml"
assert_pr_workflow_hardened "$REPO_ROOT/.github/workflows/pr-e2e.yml"
assert_pr_workflow_hardened "$REPO_ROOT/.github/workflows/pr-helm-chart.yml"
assert_pr_workflow_hardened "$REPO_ROOT/.github/workflows/pr-tests.yml"
assert_pr_workflow_hardened "$PR_SCRIPTS_WORKFLOW"
assert_pr_workflow_hardened "$SECURITY_WORKFLOW"

grep -Fq 'actions/dependency-review-action' "$SECURITY_WORKFLOW" \
  || fail "security workflow must run dependency review"
grep -Fq 'github/codeql-action/init' "$SECURITY_WORKFLOW" \
  || fail "security workflow must initialize CodeQL"
grep -Fq 'cd server && ./mvnw -q -DskipTests package' "$SECURITY_WORKFLOW" \
  || fail "security workflow must build Java with the server Maven wrapper"
grep -Fq 'security-events: write' "$SECURITY_WORKFLOW" \
  || fail "security workflow must grant SARIF upload permission"

python_source="$(find "$REPO_ROOT" \
  \( -path "$REPO_ROOT/.git" -o -path '*/node_modules' -o -path '*/.venv' \) -prune -o \
  -type f -name '*.py' -print -quit)"
if [[ -n "$python_source" ]]; then
  grep -Fq 'language: python' "$SECURITY_WORKFLOW" \
    || fail "security workflow must run Python CodeQL when Python source exists"
else
  ! grep -Fq 'language: python' "$SECURITY_WORKFLOW" \
    || fail "security workflow must not run Python CodeQL without Python source"
fi

grep -Fq '.github/workflows/security.yml' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when security workflow changes"
grep -Fq '.github/workflows/pr-cli.yml' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when PR CLI workflow changes"
grep -Fq '.github/workflows/pr-e2e.yml' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when PR E2E workflow changes"
grep -Fq '.github/workflows/pr-helm-chart.yml' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when PR Helm Chart workflow changes"
grep -Fq '.github/workflows/pr-tests.yml' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when PR Tests workflow changes"
grep -Fq '.github/workflows/publish-chart.yml' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when Chart publish workflow changes"
grep -Fq "'**/*.py'" "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when Python source changes"
grep -Fq '.env.release.example' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when release env example changes"
grep -Fq '.env.release.draft' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when release env draft changes"
grep -Fq 'compose.release.yml' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when release compose changes"
grep -Fq 'web/Dockerfile' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when the web image changes"
grep -Fq 'web/nginx.conf.template' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run when the nginx template changes"
grep -Fq 'bash scripts/tests/validate-release-config-test.sh' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run validate-release-config-test"
grep -Fq 'bash scripts/tests/nginx-forwarded-proto-test.sh' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run nginx-forwarded-proto-test"
grep -Fq 'bash scripts/tests/smoke-test-admin-mode-test.sh' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run smoke-test-admin-mode-test"
grep -Fq 'bash scripts/tests/runtime-secret-test.sh' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run runtime-secret-test"
grep -Fq 'bash scripts/tests/dev-web-host-test.sh' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run dev-web-host-test"
grep -Fq 'bash scripts/tests/workflow-security-test.sh' "$PR_SCRIPTS_WORKFLOW" \
  || fail "pr-scripts must run workflow-security-test"

echo "workflow-security-test passed"
