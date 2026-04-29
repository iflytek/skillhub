# Skill Scanner Backend Runtime Guide

## Overview

SkillHub now supports a backend-only security scanning chain around `skill-scanner`.
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
  Use `POST /scan` and pass a filesystem path. This only works when SkillHub and `skill-scanner` can see the same files.
- `upload`
  Use `POST /scan-upload` and upload the package archive. This is the safer default for split deployments.

Recommended usage:

- local development with shared filesystem: `local`
- Kubernetes or any split-service deployment: `upload`

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
      read-timeout-ms: 300000
      retry-max-attempts: 3
    stream:
      key: skillhub:scan:requests
      group: skillhub-scanners
```

Important environment variables:

- `SKILLHUB_SECURITY_SCANNER_ENABLED`
- `SKILLHUB_SECURITY_SCANNER_URL`
- `SKILLHUB_SECURITY_SCANNER_MODE`
- `SKILLHUB_SCAN_STREAM_KEY`
- `SKILLHUB_SCAN_STREAM_GROUP`

Scanner-side optional environment variables:

- `SKILL_SCANNER_LLM_API_KEY`
- `SKILL_SCANNER_LLM_MODEL`

If the LLM variables are absent, the scanner should still run with non-LLM analyzers.

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

## Failure Semantics

- scan task retries are handled by `AbstractStreamConsumer`
- final failure marks the version as `SCAN_FAILED`
- even after scan failure, a review task is still created so the package does not get stuck forever

This keeps the existing human review path intact while making scanner failures visible.
