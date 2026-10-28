# Skill Avatars: Uploads, Review, and Provider Configuration

This guide covers skill avatars, not account profile pictures. Image moderation is separate from skill-package security scanning.

## Upload or replace an avatar

1. On the publishing page, select a PNG/JPEG; for an existing skill, open its update page from My Skills. An avatar-only change does not require a new skill version.
2. Adjust the square crop and zoom, then wait for upload success. Pending or failed uploads prevent submission. Without an avatar, the skill uses its initial letter.
3. Submit, then inspect the submitted image and review state in My Skills or the update page. Public readers continue seeing the previous approved image until approval.
4. If rejected, read the reason, change the image and resubmit. A newer candidate replaces an older pending candidate; old review results cannot overwrite the new image.

Input must be PNG/JPEG, at most 2 MB, square and no more than 1024 pixels per side. The server validates and re-encodes it as PNG. Upload success means private storage succeeded, not that moderation passed. Temporary upload references expire after 24 hours; upload again if expired.

Updates retain the existing image unless removal is selected. Avatar-only removal takes effect after a successful submission; removal submitted with a package waits for the associated approval. Cancelling or failing submission leaves the previous image unchanged. There is no separate avatar-management page.

## Review behavior

An avatar submitted with a package shares its skill review: the reviewer approves or rejects both. Avatar-only changes requiring manual review enter the same queue as avatar changes and do not create a skill version. Namespace administrators review their namespace; platform skill administrators and super administrators can review namespace and global skills. Existing self-review rules apply. Administrators may remove an avatar but cannot upload one on behalf of its owner.

Super administrators configure machine and manual moderation in System Configuration (`/dashboard/admin/system-config`). Machine moderation defaults off and manual moderation on. The page manages switches, not provider credentials.

| Machine | Manual | Avatar-only changes to non-PRIVATE skills |
| --- | --- | --- |
| Off | Off | Becomes active on successful submission |
| Off | On | Waits for manual approval |
| On | Off | Becomes active after a machine pass; blocked/error candidates remain private |
| On | On | Machine moderation followed by a manual decision |

PRIVATE avatars are owner-readable and used directly without these reviews. Expanding visibility requires review for the target audience. Avatars submitted with packages must wait for the original package review regardless of avatar switches.

| Machine result | Effect |
| --- | --- |
| `PASSED` | Wait for manual review if enabled; otherwise activate the avatar-only candidate |
| `BLOCKED` | Keep the candidate private and retain the old image. Manual reviewers may reject it; overriding a false positive requires an explicit reason. Without manual moderation, the owner can request manual review |
| `ERROR` | Keep the candidate private; approval is blocked until the error is resolved and moderation retried |

A machine block is neither an upload failure nor a final human rejection. Owners can inspect rejected images and reasons. Automatic retries have a one-hour cooldown and a maximum of three calls including the first. After exhaustion, wait for the cooldown and explicitly retry or resubmit.

## Configure a machine-moderation provider

Select one startup provider: `xfyun` or `aliyun`. Runtime switches cannot change providers. Credentials are unnecessary while machine moderation is disabled. Real calls can incur provider charges; account availability and quotas must be checked by the deployment operator.

| Provider | Service | Credentials |
| --- | --- | --- |
| Xfyun | Image moderation | APPID, APIKey and APISecret |
| Aliyun | Enhanced content security, Shanghai `profilePhotoCheck` | RAM AccessKey ID and AccessKey Secret |

For Xfyun, APIKey maps to `access-key-id` and APISecret to `access-key-secret`; see the [provider API](https://www.xfyun.cn/doc/nlp/ImageModeration/API.html). For Aliyun, consult [service onboarding](https://help.aliyun.com/zh/document_detail/467828.html) and grant the required `yundun-greenweb:DescribeUploadToken` and `yundun-greenweb:ImageModeration` permissions rather than full access to your own OSS buckets.

Aliyun uses temporary upload credentials and synchronous moderation; no dedicated user-managed moderation bucket is required. This is independent of SkillHub's package/avatar storage. The adapter maps `none/low` risk to pass and `medium/high` to block; unknown, empty or failed responses do not pass. Treating `medium` as requiring review is SkillHub policy.

### Supply credentials through a private environment file

First configure the normal release stack. Outside the repository, create a private directory such as `/etc/skillhub/private` (suggested permissions: directory `700`, files `600`). Replace the path with your own. Standard Compose only passes declared settings; adding arbitrary values to `.env.release` does not inject them into the server.

Create `avatar-moderation.env` with **one** provider configuration. Replace placeholders and keep `SPRING_APPLICATION_JSON` as valid single-line JSON:

```dotenv
SPRING_APPLICATION_JSON={"skillhub":{"avatar":{"moderation":{"provider":"xfyun","xfyun":{"app-id":"YOUR_APPID","access-key-id":"YOUR_APIKEY","access-key-secret":"YOUR_APISECRET"}}}}}
```

Or:

```dotenv
SPRING_APPLICATION_JSON={"skillhub":{"avatar":{"moderation":{"provider":"aliyun","aliyun":{"access-key-id":"YOUR_ACCESS_KEY_ID","access-key-secret":"YOUR_ACCESS_KEY_SECRET"}}}}}
```

If you already use `SPRING_APPLICATION_JSON`, merge this subtree into it; do not replace other configuration or define duplicate variables. An override's `environment` takes precedence over `env_file`. Escape credential characters correctly for JSON.

Create `compose.avatar.yml` in the same private directory:

```yaml
services:
  server:
    env_file:
      - /etc/skillhub/private/avatar-moderation.env
```

From the repository root, retain your deployment's existing project name, Compose files and other options, then append the override:

```bash
make validate-release-config
docker compose --env-file .env.release \
  -f compose.release.yml \
  -f /etc/skillhub/private/compose.avatar.yml \
  up -d --no-deps server

docker compose --env-file .env.release \
  -f compose.release.yml \
  -f /etc/skillhub/private/compose.avatar.yml \
  ps server
```

This updates an existing full stack. For initial deployment, use `up -d` instead of `up -d --no-deps server`. A restart does not reload changed container environments; recreate the service and preserve the override in future updates. Other platforms should inject the same `skillhub.avatar.moderation` properties through their secret mechanism.

::: warning Xfyun outbound logging
The Xfyun interface carries `accessKeySecret` in URL parameters. The adapter avoids logging complete URLs and raw exceptions. Also disable HTTP debug logging and prevent proxies, tracing and external log collectors from capturing that request URL. Never share rendered secret-bearing Compose configuration or container environments.
:::

### Enable and verify actual moderation

1. After server health checks pass, open System Configuration as a super administrator.
2. Enable machine moderation; retaining manual review initially makes decisions observable.
3. As a skill owner, submit a **new** avatar for a non-PRIVATE test skill.
4. Inspect the machine result on the update page. If manual review is enabled, review its image and result in the skill review queue.
5. Verify pass, block and error handling: the old public image remains until approval, blocked/error candidates stay private, and manual rejection has a visible reason. Do not approve prohibited content merely for testing.

Saving credentials or enabling a switch checks configuration completeness, not real account permissions, connectivity, quota or responses. Existing candidates are not automatically rescanned after enabling a switch. Code and simulated tests do not replace acceptance with your own deployment account; verify connectivity, result mapping and failure handling after setup or credential rotation.

## Provider changes and retention

Candidates retain snapshots of their machine/manual switches and provider. Later setting changes do not rewrite old candidates. Before switching providers, finish pending reviews; update private configuration, recreate the server, then submit new candidates. Completed results are not rerun. If an old candidate still requires its old provider after a switch, it errors rather than silently using the new provider.

Rejected, replaced or deleted candidates are privately retained for 30 days before cleanup; textual review records remain. Provider-side temporary-upload retention is separate.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Provider unavailable when enabling | Provider spelling, required credentials and whether the correct override was used; do not print all environment values |
| Repeated machine errors | Server connectivity, account permissions, enabled service and quota; Aliyun also needs its temporary OSS upload endpoint. Wait for cooldown before retrying |
| Manual moderation disabled but package still needs review | The avatar switch does not change package review rules |
| Other users see the old image | Expected while the new candidate is pending; owners inspect candidates in My Skills/update, public detail shows the active avatar |
