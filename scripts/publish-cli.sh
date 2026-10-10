#!/usr/bin/env bash

# Release entrypoint for the SkillHub CLI.
#
# Runs local build-and-test (lint, typecheck, test, build), bumps the version
# in cli/package.json, pushes a release branch, and opens a PR to main.
# Tagging is done manually after the PR is merged — the tag push triggers
# `release-cli.yml` which builds and publishes to npm.

set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
CLI_DIR="$REPO_ROOT/cli"
PACKAGE_JSON="$CLI_DIR/package.json"

log_stage() {
  echo "[publish-cli] $1"
}

usage() {
  echo "Usage: $0 [patch|minor|major]" >&2
}

confirm() {
  local prompt="$1"
  local answer
  read -r -p "$prompt [y/N]: " answer
  [[ "$answer" =~ ^[Yy]$ ]]
}

BUMP_TYPE="${1:-patch}"
if [[ "$BUMP_TYPE" != "patch" && "$BUMP_TYPE" != "minor" && "$BUMP_TYPE" != "major" ]]; then
  usage
  exit 1
fi

# --- Cleanup state machine ---
#
# Tracks how far we got so the ERR trap can roll back the right pieces.
# Stages advance monotonically; the trap inspects this to decide what to undo.
#
#   pre-branch       — nothing created yet
#   on-release       — checked out release branch (may have uncommitted edits)
#   committed        — version bump committed locally, not yet pushed
#   pushed           — release branch pushed to origin (PR not yet open)
#   pr-opened        — PR opened (terminal success state, trap is a no-op)
#
CLEANUP_STAGE="pre-branch"
ORIGINAL_BRANCH=""
RELEASE_BRANCH=""

cleanup_on_error() {
  local exit_code=$?
  trap - ERR EXIT INT TERM
  set +e

  # When triggered by a signal with no failed command, $? may be 0; force a
  # non-zero exit so the caller sees the interruption.
  if [[ $exit_code -eq 0 ]]; then
    exit_code=130
  fi

  case "$CLEANUP_STAGE" in
    pre-branch)
      # build-and-test runs `bun run lint/typecheck/build`, all of which
      # regenerate cli/src/generated/pkg-info.ts via their pre-* hooks. If
      # we got interrupted mid-flight, restore it so the working tree is
      # clean for the next attempt.
      git -C "$REPO_ROOT" checkout -- "$CLI_DIR/src/generated/pkg-info.ts" 2>/dev/null
      ;;
    pr-opened)
      # Already succeeded.
      ;;
    on-release)
      echo "" >&2
      echo "[publish-cli] error before commit — rolling back release branch" >&2
      git -C "$REPO_ROOT" checkout -f "$ORIGINAL_BRANCH" 2>/dev/null
      git -C "$REPO_ROOT" branch -D "$RELEASE_BRANCH" 2>/dev/null
      ;;
    committed)
      echo "" >&2
      echo "[publish-cli] error after commit but before push — rolling back local branch" >&2
      git -C "$REPO_ROOT" checkout -f "$ORIGINAL_BRANCH" 2>/dev/null
      git -C "$REPO_ROOT" branch -D "$RELEASE_BRANCH" 2>/dev/null
      ;;
    pushed)
      echo "" >&2
      echo "ERROR: release branch '$RELEASE_BRANCH' was pushed but PR creation failed." >&2
      echo "" >&2
      echo "To recover:" >&2
      echo "" >&2
      echo "  1. Open the PR manually:" >&2
      echo "       gh pr create --base main --head $RELEASE_BRANCH" >&2
      echo "" >&2
      echo "  2. Or roll back:" >&2
      echo "       git checkout $ORIGINAL_BRANCH" >&2
      echo "       git branch -D $RELEASE_BRANCH" >&2
      echo "       git push origin --delete $RELEASE_BRANCH" >&2
      echo "" >&2
      ;;
  esac

  exit "$exit_code"
}

trap cleanup_on_error ERR INT TERM

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required (https://cli.github.com)" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "gh CLI is not authenticated. Run: gh auth login" >&2
  exit 1
fi

log_stage "checking git working tree"
if [[ -n "$(git -C "$REPO_ROOT" status --porcelain)" ]]; then
  echo "git working tree is not clean — commit or stash changes first" >&2
  exit 1
fi

CURRENT_BRANCH="$(git -C "$REPO_ROOT" rev-parse --abbrev-ref HEAD)"
if [[ "$CURRENT_BRANCH" != "main" ]]; then
  echo "releases must be cut from 'main' (current: '$CURRENT_BRANCH')" >&2
  exit 1
fi
ORIGINAL_BRANCH="$CURRENT_BRANCH"

log_stage "pulling latest from origin/$CURRENT_BRANCH"
git -C "$REPO_ROOT" pull --ff-only origin "$CURRENT_BRANCH"

# --- Compute version & pre-flight checks (cheap, run before build) ---
#
# Baseline is read from origin via `ls-remote`, not from local tags. A local
# orphan tag left over from a failed `git push origin cli-vX.Y.Z` must not
# influence the next version — otherwise we'd skip versions or release on top
# of something that was never published.

log_stage "computing next version from latest origin cli-v* tag"
LATEST_TAG="$(git -C "$REPO_ROOT" ls-remote --tags --refs origin 'cli-v*' \
  | awk '{sub(/^refs\/tags\//, "", $2); print $2}' \
  | sort -V \
  | tail -n1)"
if [[ -n "$LATEST_TAG" ]]; then
  BASE_VERSION="${LATEST_TAG#cli-v}"
  # Reject prerelease tags (e.g., cli-v0.2.0-rc.1). Only pure X.Y.Z is supported.
  if [[ "$BASE_VERSION" =~ [^0-9.] ]]; then
    echo "latest origin tag $LATEST_TAG contains prerelease suffix: $BASE_VERSION" >&2
    echo "this script only supports pure X.Y.Z versions" >&2
    echo "skip prerelease tags manually or use a different baseline" >&2
    exit 1
  fi
  log_stage "baseline: $BASE_VERSION (from origin $LATEST_TAG)"
else
  BASE_VERSION="$(PACKAGE_JSON="$PACKAGE_JSON" node -p "require(process.env.PACKAGE_JSON).version")"
  log_stage "no cli-v* tags on origin, baseline: $BASE_VERSION (from package.json)"
fi

NEW_VERSION="$(BASE_VERSION="$BASE_VERSION" BUMP_TYPE="$BUMP_TYPE" node -e "
  const v = process.env.BASE_VERSION.split('.').map(Number);
  if (v.length !== 3 || v.some(Number.isNaN)) {
    console.error('invalid baseline version: ' + process.env.BASE_VERSION);
    process.exit(1);
  }
  const t = process.env.BUMP_TYPE;
  if (t === 'patch') v[2]++;
  else if (t === 'minor') { v[1]++; v[2] = 0; }
  else if (t === 'major') { v[0]++; v[1] = 0; v[2] = 0; }
  console.log(v.join('.'));
")"

if [[ -z "$NEW_VERSION" ]]; then
  echo "failed to compute new version from baseline $BASE_VERSION" >&2
  exit 1
fi

TAG="cli-v${NEW_VERSION}"
RELEASE_BRANCH="release/${TAG}"

if git -C "$REPO_ROOT" rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
  echo "tag $TAG already exists locally" >&2
  exit 1
fi

if git -C "$REPO_ROOT" ls-remote --exit-code --tags origin "refs/tags/$TAG" >/dev/null 2>&1; then
  echo "tag $TAG already exists on origin" >&2
  exit 1
fi

if git -C "$REPO_ROOT" rev-parse -q --verify "refs/heads/$RELEASE_BRANCH" >/dev/null; then
  echo "branch $RELEASE_BRANCH already exists locally" >&2
  exit 1
fi

if git -C "$REPO_ROOT" ls-remote --exit-code --heads origin "refs/heads/$RELEASE_BRANCH" >/dev/null 2>&1; then
  echo "branch $RELEASE_BRANCH already exists on origin" >&2
  exit 1
fi

log_stage "target: $NEW_VERSION (branch: $RELEASE_BRANCH)"

# --- Local build-and-test ---

log_stage "installing dependencies"
(cd "$CLI_DIR" && bun install --frozen-lockfile)

log_stage "running lint"
(cd "$CLI_DIR" && bun run lint)

log_stage "running typecheck"
(cd "$CLI_DIR" && bun run typecheck)

log_stage "running tests"
(cd "$CLI_DIR" && bun test)

log_stage "running build"
(cd "$CLI_DIR" && bun run build)

log_stage "build-and-test passed"

# Reset only the codegen file that build-and-test regenerates. We rewrite
# pkg-info.ts again after the version bump, so this just keeps the working
# tree clean before branching.
git -C "$REPO_ROOT" checkout -- "$CLI_DIR/src/generated/pkg-info.ts"

if ! confirm "Push $RELEASE_BRANCH and open PR to main?"; then
  echo "release cancelled" >&2
  exit 1
fi

# --- Create branch, bump, push, open PR ---

log_stage "creating release branch $RELEASE_BRANCH"
git -C "$REPO_ROOT" checkout -b "$RELEASE_BRANCH"
CLEANUP_STAGE="on-release"

log_stage "writing version $NEW_VERSION to package.json"
NEW_VERSION="$NEW_VERSION" PACKAGE_JSON="$PACKAGE_JSON" node -e "
  const fs = require('fs');
  const pkg = JSON.parse(fs.readFileSync(process.env.PACKAGE_JSON, 'utf8'));
  pkg.version = process.env.NEW_VERSION;
  fs.writeFileSync(process.env.PACKAGE_JSON, JSON.stringify(pkg, null, 2) + '\n');
"

log_stage "regenerating pkg-info.ts with new version"
(cd "$CLI_DIR" && bun run scripts/generate-pkg-info.ts)

log_stage "committing version bump"
git -C "$REPO_ROOT" add "$PACKAGE_JSON" "$CLI_DIR/src/generated/pkg-info.ts"
git -C "$REPO_ROOT" commit -m "chore(cli): bump version to $NEW_VERSION"
CLEANUP_STAGE="committed"

log_stage "pushing release branch to origin"
git -C "$REPO_ROOT" push -u origin "$RELEASE_BRANCH"
CLEANUP_STAGE="pushed"

log_stage "opening pull request"
PR_BODY="Bumps CLI version to \`$NEW_VERSION\`.

Local build-and-test passed (lint, typecheck, test, build).

After merging, tag and push to trigger the release:
\`\`\`bash
git fetch origin main
git checkout main
git merge --ff-only origin/main
git tag $TAG origin/main
git push origin $TAG
\`\`\`

Watch the release: https://github.com/iflytek/skillhub/actions/workflows/release-cli.yml"

gh pr create \
    --base main \
    --head "$RELEASE_BRANCH" \
    --title "chore(cli): release $NEW_VERSION" \
    --body "$PR_BODY"
CLEANUP_STAGE="pr-opened"

log_stage "returning to $CURRENT_BRANCH"
git -C "$REPO_ROOT" checkout "$CURRENT_BRANCH"
git -C "$REPO_ROOT" branch -D "$RELEASE_BRANCH"

log_stage "done — PR opened. After merge, tag manually:"
echo ""
echo "  git fetch origin main"
echo "  git tag $TAG origin/main"
echo "  git push origin $TAG"
echo ""
