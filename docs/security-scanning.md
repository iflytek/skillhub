# Skill Scanner Backend Runtime Guide

> **Document status:** This is a historical backend implementation note, updated with the
> current Scanner 2.1.0 runtime and rollout constraints. It does not expand the supported
> analyzer or deployment contract.

## Overview

SkillHub now supports a backend-only security scanning chain around `skill-scanner`.
The Scanner image pins `cisco-ai-skill-scanner==2.1.0` and uses a glibc-based Linux runtime.
Published images support `linux/amd64` and `linux/arm64`.
The publish flow changes are:

1. publish request enters `SkillPublishService`
2. if scanner is enabled, the version moves to `SCANNING`
3. the backend enqueues a `ScanTask`
4. `ScanTaskConsumer` calls `skill-scanner`
5. the scan result is stored in `security_audit`
6. the version moves to `PENDING_REVIEW`, or to `SCAN_FAILED` after final retry exhaustion
7. review still happens through the existing review workflow

Frontend is intentionally out of scope here. The frontend should fetch audit details through the dedicated backend API instead of expecting scanner data inside existing review detail payloads.

## Runtime Modes

Two runtime modes are supported:

- `local`
  Use `POST /scan` and pass a filesystem path. SkillHub and `skill-scanner` must mount the same
  directory at the same path. The Scanner must also allow that root; for the standard path, set
  `SKILL_SCANNER_ALLOWED_ROOTS=/tmp/skillhub-scans`.
- `upload`
  Use `POST /scan-upload` and upload the package archive. This is the mode used by the official
  Compose and Kubernetes deployments.

Recommended usage:

- local development with shared filesystem: `local`
- official Compose, Kubernetes, or any split-service deployment: `upload`

## Backend Configuration

Application properties:

```yaml
skillhub:
  security:
    scanner:
      enabled: false
      base-url: http://localhost:8000
      health-path: /health
      scan-path: /scan-upload
      mode: local
      connect-timeout-ms: 5000
      read-timeout-ms: 900000
      retry-max-attempts: 3
    stream:
      key: skillhub:scan:requests
      group: skillhub-scanners
      reclaim-min-idle: PT16M
      max-unavailable-age: PT1H
```

Important environment variables:

- `SKILLHUB_SECURITY_SCANNER_ENABLED`
- `SKILLHUB_SECURITY_SCANNER_URL`
- `SKILLHUB_SECURITY_SCANNER_MODE`
- `SKILLHUB_SECURITY_SCANNER_READ_TIMEOUT`
- `SKILLHUB_SCAN_STREAM_KEY`
- `SKILLHUB_SCAN_STREAM_GROUP`
- `SKILLHUB_SCAN_STREAM_RECLAIM_MIN_IDLE`
- `SKILLHUB_SECURITY_STREAM_MAX_UNAVAILABLE_AGE`

Scanner-side optional environment variables:

- `SKILL_SCANNER_LLM_API_KEY`
- `SKILL_SCANNER_LLM_BASE_URL`
- `SKILL_SCANNER_LLM_MODEL`
- `SKILLHUB_SCANNER_MAX_CONCURRENT_SCANS` (default `1`)
- `SKILLHUB_SCANNER_HARD_TIMEOUT_SECONDS` (default `930`)
- `SKILLHUB_SCANNER_MAX_UPLOAD_SIZE_BYTES` (default `110100480`, or 105 MiB)

If the LLM variables are absent, the scanner should still run with non-LLM analyzers.
The default timeout ordering is server read timeout (900 seconds), scanner hard timeout
(930 seconds), then pending-message reclaim (960 seconds). A hard timeout exits the scanner process
with status `124`; Compose or Kubernetes restarts it and the Redis pending task is retried.

## Kubernetes Notes

Current repository manifests assume **separate** `skillhub-server` and `skillhub-scanner` deployments.
Because these deployments do not share a writable package directory, Kubernetes should use:

```text
SKILLHUB_SECURITY_SCANNER_MODE=upload
SKILLHUB_SECURITY_SCANNER_URL=http://skillhub-scanner:8000
```

Relevant manifests:

- `deploy/k8s/scanner-deployment.yaml`
- `deploy/k8s/services.yaml`
- `deploy/k8s/backend-deployment.yaml`
- `deploy/k8s/configmap.yaml`

The scanner service is internal-only by default and is consumed by the backend through cluster DNS.

## Rolling Upgrade to Scanner 2.1.0

The Server and Scanner HTTP contracts must be upgraded in this order:

1. deploy the compatibility Server release while the old Scanner is still running
2. drain and remove every old Server instance, including in-flight scan requests
3. upgrade the Scanner to 2.1.0
4. verify `/health` and an upload-mode scan before restoring normal traffic

Do not run an old Server against Scanner 2.1.0. During a mixed-version rollout in either scan mode,
keep AI Defense disabled (`SKILLHUB_SCANNER_USE_AI_DEFENSE=false`, the default). If AI Defense must
remain enabled before the old Scanner is retired, configure its credential directly in the old
Scanner environment using the variable supported by that Scanner version. Never place an AI Defense
key in URL query parameters or request bodies.

## Verification

Verify the scanner service itself:

```bash
sh scripts/verify-scanner.sh http://localhost:8000
sh scripts/verify-scanner.sh http://localhost:8000 /path/to/skill.zip
```

Recommended backend checks after enabling the feature:

1. publish a test package
2. confirm the version status becomes `SCANNING`
3. confirm a `security_audit` row is created
4. confirm the version eventually moves to `PENDING_REVIEW` or `SCAN_FAILED`
5. call `GET /api/v1/skills/{skillId}/versions/{versionId}/security-audit`

## Audit Query API

Backend audit data is available from:

```text
GET /api/v1/skills/{skillId}/versions/{versionId}/security-audit
```

Response fields include:

- `scanId`
- `scannerType`
- `verdict`
- `isSafe`
- `maxSeverity`
- `findingsCount`
- `findings`
- `scanDurationSeconds`
- `scannedAt`
- `createdAt`

`isSafe: true` means the scan found no high-risk issue. It does not mean that the scan produced no
findings: lower-severity findings may still be present and `findingsCount` may be non-zero. The UI
therefore renders this state as **No high-risk findings**, not as an unconditional safety guarantee.

## Failure Semantics

- scan task retries are handled by `AbstractStreamConsumer`
- scanner connection failures, HTTP 429, and HTTP 5xx remain pending for automatic recovery
- unavailable tasks older than `max-unavailable-age` are marked `SCAN_FAILED`, acknowledged, and removed from the Redis Stream
- the timeout is evaluated during pending reclaim; terminal handling can occur roughly one `reclaim-min-idle` plus one `reclaim-interval` after the configured age
- terminal failures retain a failure reason in the security audit response for operators and authorized users
- other final failures mark the version as `SCAN_FAILED` after retry exhaustion
- even after scan failure, a review task is still created so the package does not get stuck forever

This keeps the existing human review path intact while making scanner failures visible.
