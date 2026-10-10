# CLI Release Guide

## Overview

CLI releases use a PR-based flow. Running `make publish-cli` on a clean `main` branch:

1. Runs local build-and-test (lint, typecheck, test, build)
2. Computes the next version from the latest `cli-v*` tag on `origin`
3. Creates a `release/cli-vX.Y.Z` branch with the version bump committed
4. Pushes the branch and opens a PR to `main`

After the PR is merged, you manually tag and push — the tag triggers [`release-cli.yml`](../.github/workflows/release-cli.yml) which builds, publishes to npm, and creates a GitHub Release.

## Prerequisites

### Repository Secrets

Configure in GitHub repository → Settings → Secrets and variables → Actions:

- `NPM_TOKEN`: npm token with publish permissions
  - Generate at https://www.npmjs.com/settings/YOUR_USERNAME/tokens
  - Use **Classic Automation Token** (bypasses 2FA automatically), or
  - **Granular Access Token** with "Allow bypass 2FA" enabled, scoped to the package

### Repository Variables (optional)

- `NPM_REGISTRY`: npm registry URL (default: `https://registry.npmjs.org`)

### Local Environment

- `node` and `bun` installed
- `gh` CLI installed and authenticated (`gh auth login`)
- `git` with push access to the repository
- On the `main` branch with a clean working tree

### Package Configuration

In [`cli/package.json`](./package.json):

```json
{
  "name": "@astron-team/skillhub",
  "publishConfig": {
    "access": "public"
  }
}
```

## Release Process

### Step 1: Run the publish script

From the repository root, on a clean `main` branch:

```bash
make publish-cli         # patch: 0.1.5 -> 0.1.6
make publish-cli-minor   # minor: 0.1.5 -> 0.2.0
make publish-cli-major   # major: 0.1.5 -> 1.0.0
```

[`scripts/publish-cli.sh`](../scripts/publish-cli.sh) performs the following steps:

1. Verify `gh` CLI is installed and authenticated
2. Verify the working tree is clean and on `main`
3. `git pull --ff-only` from `origin/main`
4. Run full local build-and-test (lint, typecheck, test, build)
5. Compute the next version from the latest `cli-v*` tag on `origin` (via `git ls-remote`, so local orphan tags from a failed `git push origin cli-vX.Y.Z` are ignored)
6. Verify the tag and release branch don't already exist
7. After interactive confirmation: create release branch, commit version bump, push, and open PR

### Step 2: Merge the PR

Review and merge the PR on GitHub as usual.

### Step 3: Tag and push

After the PR is merged:

```bash
git fetch origin main
git tag cli-vX.Y.Z origin/main   # replace with the actual version
git push origin cli-vX.Y.Z
```

This ensures the tag is always placed on the merge commit on `origin/main`, regardless of your local branch state.

Pushing the tag triggers CI which builds, publishes to npm, and creates a GitHub Release.

### CI Workflow

[`release-cli.yml`](../.github/workflows/release-cli.yml) contains three jobs:

1. **build-and-test**
   - Extract version from tag name (`cli-v0.1.6` → `0.1.6`) and write it into `cli/package.json`
   - Install deps, lint, typecheck, test, build
   - Verify the built CLI's runtime version matches the tag

2. **publish-npm**
   - Skip if the target version already exists on the registry
   - Configure `~/.npmrc` and run `npm publish --access public`

3. **create-release**
   - Package `dist/` + README + LICENSE as `tar.gz` and `zip`
   - Generate SHA256 checksums
   - Create a GitHub Release and upload artifacts

### Verify Release

- Workflow: https://github.com/iflytek/skillhub/actions/workflows/release-cli.yml
- Release: https://github.com/iflytek/skillhub/releases
- npm: `npm view @astron-team/skillhub@<version>`

## Release Audit Trail

GitHub Actions automatically records on each workflow run page:

- **Triggering user** (the developer who pushed the tag, i.e. `github.actor`)
- **Trigger event** (`push` tag or `workflow_dispatch`)
- **Tag name and commit SHA**

The team can review the full audit trail in the Actions tab without any extra configuration.

## Manual Trigger

From the Actions UI:

1. Actions → Release CLI → "Run workflow"
2. Enter an existing tag name matching `cli-vX.Y.Z`
3. Optionally enable skip npm publish

## Error Recovery

The script uses a cleanup state machine. If it fails at different stages:

- **Before push**: release branch is deleted locally, you're returned to `main`
- **After push, before PR**: the script prints recovery instructions (open PR manually or delete the remote branch)
- **After PR opened**: success — no cleanup needed

If you need to manually clean up a failed release:

```bash
git checkout main
git branch -D release/cli-vX.Y.Z           # delete local branch
git push origin --delete release/cli-vX.Y.Z # delete remote branch (if pushed)
```

## Troubleshooting

### `releases must be cut from 'main'`

Switch back to `main`, pull the latest, and retry.

### `git working tree is not clean`

Commit or stash local changes first.

### `tag cli-vX.Y.Z already exists`

The previous release didn't clean up, or someone else released the same version. Check `git tag --list 'cli-v*'` and remote tags, then retry with a higher version.

### `branch release/cli-vX.Y.Z already exists`

A previous release attempt left a stale branch. Delete it locally and/or on origin, then retry.

### npm Publish Fails

- **403 with 2FA message**: `NPM_TOKEN` is not an Automation Token, or bypass 2FA is not enabled — regenerate with the correct type
- **403 Forbidden**: Package scope doesn't match token permissions — confirm publish rights for the `@astron-team` org
- **E404**: The registry doesn't host this scope — check `NPM_REGISTRY`

### Build / Test Fails

Reproduce locally:

```bash
make lint-cli && make typecheck-cli && make test-cli && make build-cli
```

Confirm the Bun version matches `packageManager` in [`cli/package.json`](./package.json).

### Version Mismatch (runtime ≠ tag)

CI runs `node dist/index.js version` and requires the output to match the tag. If the CLI's `version` command implementation changes, update the verification logic in [`release-cli.yml`](../.github/workflows/release-cli.yml) accordingly.

## Tag Naming Convention

- CLI releases: `cli-v*` (e.g., `cli-v0.1.6`)
- Repository releases: `v*` (e.g., `v0.3.0`)

The two tag namespaces are independent, allowing CLI and server to version separately.
