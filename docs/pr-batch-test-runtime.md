# PR Batch Test Runtime

This repository includes a manual GitHub Actions workflow that builds a
synthetic test image set from multiple PRs and deploys it to the shared
Hong Kong manual-test machine.

Workflow file:

- `.github/workflows/pr-batch-test-deploy.yml`

## What the workflow does

When you trigger the workflow manually, it:

1. checks out the repository and fetches the selected base branch
2. parses the PR list you provide and deduplicates it while preserving order
3. verifies that every PR is still open and targets the chosen base branch
4. merges the selected PR heads onto the base branch in the exact order you supplied
5. fails fast if any PR conflicts with the base branch or with an earlier PR in the batch
6. builds `server`, `web`, and `scanner` images for `linux/amd64`
7. pushes both a floating tag and an immutable tag to GHCR
8. SSHes into the HK test machine as a dedicated deploy user
9. updates the selected non-secret runtime fields in `/opt/skillhub-runtime/.env.release`
10. calls a root-owned deployment wrapper through `sudo`
11. runs `docker compose pull && docker compose up -d` through the remote wrapper

The floating tag is the shared environment channel. By default it is
`manual-test-hk`. Each run also pushes an immutable tag for traceability:

- floating tag example: `manual-test-hk`
- immutable tag example: `manual-test-hk-128-3d4a8e7f9a1b`

The runtime always deploys the floating tag, so the same test URL keeps
working while still letting maintainers look up the exact image version
used by a given run.

## Required GitHub secrets

Add these repository or environment secrets before using the workflow:

- `TEST_RUNTIME_SSH_HOST`: test machine hostname or IP
- `TEST_RUNTIME_SSH_KEY`: private key content used by GitHub Actions

Optional secrets:

- `TEST_RUNTIME_SSH_USER`: defaults to `skillhub-deploy`
- `TEST_RUNTIME_SSH_PORT`: defaults to `22`

The remote machine should expose a root-owned deployment command at:

- `/usr/local/bin/skillhub-test-deploy`

The dedicated deploy user is expected to have passwordless sudo access to
that command only.

## Recommended usage

Open the workflow in GitHub Actions and fill in:

- `pr_numbers`: a comma-separated or newline-separated list such as `123, 124, 130`
- `base_ref`: usually `main`
- `deploy_channel`: keep the default `manual-test-hk` for the shared test machine
- `public_url`: the public URL to write into `SKILLHUB_PUBLIC_BASE_URL`
- `web_base_path`: optional sub-path to write into `SKILLHUB_WEB_BASE_PATH`; when set,
  the workflow also writes `SKILLHUB_WEB_API_BASE_URL` to the same path without the
  trailing slash

The merge order matters. If PR `124` depends on `123`, list `123` first.

For sub-path verification, use a unique deploy channel so Compose recreates the
containers even if the shared floating tag already exists:

```text
deploy_channel=manual-test-hk-<short-sha>
public_url=https://skill.xf-yun.com.cn/skillhub
web_base_path=/skillhub/
```

The workflow must verify that `/skillhub/runtime-config.js` returns the generated
JavaScript runtime config, not the SPA fallback HTML.

## OSS quickstart synchronization

The one-line quickstart downloads `runtime.sh`, and that script downloads:

- `compose.release.yml`
- `.env.release.example`

When runtime flags, release Compose wiring, or release env defaults change, publish
all three files to the OSS origin together. If `--aliyun` or another mirror registry
is used, the corresponding `skillhub-server`, `skillhub-web`, and
`skillhub-scanner` image tags must also be mirrored.

## Runtime metadata on the server

After deployment, the workflow writes a small metadata file here:

- `/opt/skillhub-runtime/manual-test-deployment.txt`

It records:

- deploy time
- floating tag
- immutable tag
- merged synthetic SHA
- PR list
- GitHub Actions run URL

This makes it easy for testers and maintainers to confirm which batch is
currently deployed.
