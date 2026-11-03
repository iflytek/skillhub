# Configure avatar machine moderation

This page is for deployment operators. It covers service activation, credential injection, service updates, and actual-call verification for avatar moderation. A running registry and an image containing this feature are prerequisites. It does not deploy a separate moderation engine or explain owner uploads. Submission, policy, and review entry points are covered by [Skill avatars and moderation](./skill-avatars.html).

## Prerequisites and responsibilities

| Operation | Responsible actor | Prerequisite and completion criterion |
| --- | --- | --- |
| Inject credentials and update containers | Operator with deployment permission | A running registry and access to its deployment/private configuration; update the original project service |
| Enable moderation policy | Super administrator (`SUPER_ADMIN`) | Save switches after configuration is present; saving does not prove actual cloud calls |
| Create an acceptance candidate | Skill owner | Submit a new avatar to a non-private test skill; old applications do not prove the new setup |
| Check review and public outcomes | Authorized skill reviewer | Compare the candidate and machine result in the existing skill application; prohibited content must not be approved for testing |

## Select a provider and confirm permissions


Select one startup provider: `xfyun` or `aliyun`. Runtime switches cannot change providers. Credentials are unnecessary while machine moderation is disabled. Real calls can incur provider charges; account availability and quotas must be checked by the deployment operator.

| Provider | Service | Credentials |
| --- | --- | --- |
| Xfyun | Image moderation | APPID, APIKey and APISecret |
| Aliyun | Enhanced content security, Shanghai `profilePhotoCheck` | RAM AccessKey ID and AccessKey Secret |

For Xfyun, APIKey maps to `access-key-id` and APISecret to `access-key-secret`; see the [provider API (Chinese)](https://www.xfyun.cn/doc/nlp/ImageModeration/API.html). For Aliyun, consult [service onboarding (Chinese)](https://help.aliyun.com/zh/document_detail/467828.html) and grant the required `yundun-greenweb:DescribeUploadToken` and `yundun-greenweb:ImageModeration` permissions rather than full access to the deployment’s OSS buckets.

Aliyun uses temporary upload credentials and synchronous moderation; no dedicated user-managed moderation bucket is required. This is independent of SkillHub's package/avatar storage. The adapter maps `none/low` risk to pass and `medium/high` to block; unknown, empty or failed responses do not pass. Human handling of a blocked result follows the avatar moderation policy; it is not a final human decision by the provider.

## Inject private configuration and recreate the service

First configure the normal release stack. Create a private directory outside the repository and replace `/etc/skillhub/private` with its actual path. The following filesystem permissions are this page's example, not application defaults. Credential files must not be committed to Git.

| Object | Example permissions | Effect and boundary |
| --- | --- | --- |
| Private directory | `700` | Set by the deployment operator; only its owner may access it |
| Credential and override files | `600` | Set by the deployment operator; only their owner may read or write them |

Standard Compose only passes declared settings; adding arbitrary values to `.env.release` does not inject them into the server.

Create `avatar-moderation.env` with **one** provider configuration. Replace placeholders and keep `SPRING_APPLICATION_JSON` as valid single-line JSON:

```dotenv
SPRING_APPLICATION_JSON={"skillhub":{"avatar":{"moderation":{"provider":"xfyun","xfyun":{"app-id":"YOUR_APPID","access-key-id":"YOUR_APIKEY","access-key-secret":"YOUR_APISECRET"}}}}}
```

Or:

```dotenv
SPRING_APPLICATION_JSON={"skillhub":{"avatar":{"moderation":{"provider":"aliyun","aliyun":{"access-key-id":"YOUR_ACCESS_KEY_ID","access-key-secret":"YOUR_ACCESS_KEY_SECRET"}}}}}
```

If the deployment already uses `SPRING_APPLICATION_JSON`, merge this subtree into it; do not replace other configuration or define duplicate variables. An override's `environment` takes precedence over `env_file`. Escape credential characters correctly for JSON.

Create `compose.avatar.yml` in the same private directory:

```yaml
services:
  server:
    env_file:
      - /etc/skillhub/private/avatar-moderation.env
```

From the repository root, retain the deployment’s existing project name, Compose files and other options, then append the override:

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

## Enable moderation and verify actual results

1. After server health checks pass, open System Configuration as a super administrator.
2. Enable machine moderation; retaining manual review initially makes decisions observable.
3. As a skill owner, submit a **new** avatar for a non-PRIVATE test skill.
4. Inspect the machine result on the update page. If manual review is enabled, review its image and result in the skill review queue.
5. Verify pass, block and error handling: the old public image remains until approval, blocked/error candidates stay private, and manual rejection has a visible reason. Do not approve prohibited content merely for testing.

Saving credentials or enabling a switch checks configuration completeness, not real account permissions, connectivity, quota or responses. Existing candidates are not automatically rescanned after enabling a switch. Code and simulated tests do not replace acceptance with the deployment account; verify connectivity, result mapping and failure handling after setup or credential rotation.

## Change providers and handle existing applications

Each application retains snapshots of its machine/manual switches and provider. Later configuration does not rewrite applications or rerun completed results. Before switching, authorized reviewers should handle pending applications. Update private configuration and recreate `server`, then submit a new candidate as its skill owner to use the new provider.

An old candidate still requiring its original provider returns `ERROR` after a provider switch; it does not silently use the new provider. Changing only an administration switch does not recover that task. Candidate retention is defined in [Skill avatars and moderation](./skill-avatars.html); provider-side temporary uploads follow external service rules and are a separate lifecycle.

## Completion criteria and troubleshooting

Enabling machine moderation only confirms that the selected provider's required configuration is present. Verify integration through actual results and candidate visibility on a new application. Health, supplied credentials, and saved switches do not replace actual-call acceptance.

| Symptom | Likely cause | Diagnostic action | Resolution |
| --- | --- | --- | --- |
| Provider unavailable when enabling | Invalid `provider` or missing injected credentials | Compare private property names and whether the startup command used the same override | Correct configuration and recreate the original service; do not print full environments or Secrets |
| Behavior unchanged after configuration edits | Container was only restarted or another value overrides the setting | Compare Compose file order and `environment`/`env_file` definitions | Merge the JSON and recreate the service; do not overwrite unrelated Spring settings |
| A new application's machine state is `ERROR` | Service activation, permissions, quota, or outbound network failure | Inspect its machine state and provider account settings; Aliyun also requires its temporary OSS upload endpoint | Restore dependencies before retrying under cooldown rules; do not interpret errors as passes |
| Old candidates fail after switching | The saved provider differs from the active deployment | Compare the candidate provider with the current `provider` | Handle the old application or resubmit; do not directly edit candidate snapshots |
| Existing applications have no new result after enabling | Switch changes do not rescan old applications | Check candidate time and machine state on the update page | Verify through a new application; do not repeatedly approve old applications to trigger calls |

Configuration sources: [Provider gateway](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AvatarModerationGateway.java), [Xfyun adapter](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/XfyunAvatarModerationProvider.java), and [Aliyun adapter](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AliyunAvatarModerationProvider.java). Commands use [release Compose](https://github.com/iflytek/skillhub/blob/main/compose.release.yml). Other deployment paths must inject equivalent properties through their Secret mechanism rather than copying Compose update commands.
