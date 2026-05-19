# CLI Release Guide

## Overview

CLI releases are fully automated. Running `make publish-cli` on a clean `main` branch bumps the version, commits, creates a `cli-vX.Y.Z` tag, and pushes everything to origin. The GitHub Actions workflow [`release-cli.yml`](../.github/workflows/release-cli.yml) listens for the tag and handles build, test, npm publish, and GitHub Release creation.

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

- `node` and `npm` installed (the script uses `npm version` to bump)
- `git` installed with push access to the repository
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

### One-shot Release

From the repository root, on a clean `main` branch:

```bash
make publish-cli         # patch: 0.1.5 -> 0.1.6
make publish-cli-minor   # minor: 0.1.5 -> 0.2.0
make publish-cli-major   # major: 0.1.5 -> 1.0.0
```

[`scripts/publish-cli.sh`](../scripts/publish-cli.sh) performs the following steps:

1. Verify the working tree is clean
2. Require the current branch to be `main`, otherwise abort
3. `git pull --ff-only` from `origin/main`
4. Fetch remote tags and align `package.json` with the latest `cli-v*` tag
5. Compute the new version via `npm version <bump>`
6. Verify the new tag does not exist locally or on origin
7. After interactive confirmation: commit the bump, create the `cli-vX.Y.Z` tag, push both commit and tag to origin

Pushing the tag triggers CI — no further manual action required.

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

## Troubleshooting

### `releases must be cut from 'main'`

Switch back to `main`, pull the latest, and retry.

### `git working tree is not clean`

Commit or stash local changes first.

### `tag cli-vX.Y.Z already exists`

The previous release didn't clean up, or someone else released the same version. Check `git tag --list 'cli-v*'` and remote tags, then retry with a higher version.

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
