# Security Scanning Integration

## Overview

SkillHub integrates [cisco-ai-defense/skill-scanner](https://github.com/cisco-ai-defense/skill-scanner) to perform security analysis on skill packages before they enter the review pipeline. The scanner runs as an independent Python container, communicating with the Java backend via Redis Stream + shared volume.

## Architecture

```
User publishes skill
        │
        ▼
┌─────────────────────┐
│  SkillPublishService │  1. Validate package
│  (skillhub-domain)   │  2. Save files to /tmp/skillhub-scans/{versionId}/
│                      │  3. Set version status → SCANNING
│                      │  4. Publish ScanTask to Redis Stream
└──────────┬──────────┘
           │ Redis Stream: skillhub:scan:requests
           ▼
┌─────────────────────┐
│  ScanTaskConsumer    │  Extends AbstractStreamConsumer<T>
│  (skillhub-app)      │  Consumer group with auto-ack, retry (max 3)
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐     HTTP POST /scan
│  SkillScannerAdapter │ ──────────────────► skill-scanner container
│  (skillhub-infra)    │                     (reads files from shared volume)
│                      │ ◄──────────────────
│  mode: local|upload  │     scan results
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  SecurityScanService │  1. Save SecurityAudit record
│  (skillhub-domain)   │  2. Set version status → PENDING_REVIEW
│                      │  3. Publish ScanCompletedEvent
└──────────┬──────────┘
           │ Spring ApplicationEvent
           ▼
┌──────────────────────────┐
│  ScanCompletedEventListener │  Create ReviewTask
│  (skillhub-domain)          │  (new transaction, after commit)
└──────────────────────────┘
```

## Module Responsibilities

### skillhub-domain

Core business logic, no framework dependencies beyond Spring annotations.

| Class | Role |
|-------|------|
| `SecurityScanService` | Orchestrates scan lifecycle: trigger → process result → publish event |
| `SecurityScanner` | Interface for scan execution |
| `ScanTaskProducer` | Interface for publishing scan tasks |
| `SecurityAudit` | JPA entity, stores scan results in `security_audit` table |
| `ScanCompletedEvent` | Spring event carrying versionId, verdict, findingsCount |
| `ScanCompletedEventListener` | `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)` — creates ReviewTask |
| `SecurityVerdict` | Enum: SAFE, SUSPICIOUS, DANGEROUS, BLOCKED |

### skillhub-infra

Infrastructure implementations.

| Class | Role |
|-------|------|
| `SkillScannerAdapter` | Implements `SecurityScanner`, delegates to `SkillScannerService`, maps API response to domain model |
| `SkillScannerService` | HTTP calls to skill-scanner API (`/scan` for local, `/scan-upload` for upload mode) |
| `SkillScannerApiResponse` | Jackson record mapping skill-scanner JSON response |
| `HttpClient` / `WebClientHttpClient` | Generic HTTP client abstraction over Spring WebClient |

### skillhub-app

Configuration and stream consumers.

| Class | Role |
|-------|------|
| `SkillScannerConfig` | Creates `SkillScannerService` and `SecurityScanner` beans (conditional on `scanner.enabled=true`) |
| `SkillScannerProperties` | `@ConfigurationProperties(prefix = "skillhub.security.scanner")` |
| `RedisStreamConfig` | Creates `RedisScanTaskProducer` and `ScanTaskConsumer` beans (conditional on `scanner.enabled=true`) |
| `AbstractStreamConsumer<T>` | Template for Redis Stream consumers: lifecycle, consumer group init, auto-ack, retry |
| `ScanTaskConsumer` | Concrete consumer: parse message → call scanner → process result → cleanup temp files |
| `RedisScanTaskProducer` | Implements `ScanTaskProducer`, publishes to Redis Stream |
| `SecurityAuditController` | REST endpoint `GET /api/v1/skills/{skillId}/versions/{versionId}/security-audit` |

## Scan Modes

| Mode | How it works | When to use |
|------|-------------|-------------|
| `local` | Server writes files to shared volume, scanner reads from same path | Same host / Docker Compose (default) |
| `upload` | Server uploads ZIP via `POST /scan-upload` multipart | Separate hosts / Kubernetes |

## Configuration

### application.yml

```yaml
skillhub:
  security:
    scanner:
      enabled: ${SKILLHUB_SECURITY_SCANNER_ENABLED:false}
      base-url: ${SKILLHUB_SECURITY_SCANNER_URL:http://localhost:8000}
      health-path: /health
      scan-path: /scan-upload
      mode: ${SKILLHUB_SECURITY_SCANNER_MODE:local}
      connect-timeout-ms: 5000
      read-timeout-ms: 300000
      retry-max-attempts: 3
    stream:
      key: ${SKILLHUB_SCAN_STREAM_KEY:skillhub:scan:requests}
      group: ${SKILLHUB_SCAN_STREAM_GROUP:skillhub-scanners}
      consumer: ${SKILLHUB_SCAN_STREAM_CONSUMER:scanner-1}
```

### Docker Compose

Scanner uses `profiles: [scanner]`, not started by default:

```bash
# Dev: start scanner alongside other services
docker compose --profile scanner up -d

# Staging: full environment with scanner
make staging-scanner
```

Server and scanner share `/tmp/skillhub-scans` via bind mount (dev) or named volume (staging).

### Scanner Environment Variables

| Variable | Description | Required |
|----------|-------------|----------|
| `SKILL_SCANNER_LLM_API_KEY` | LLM API key for semantic analysis | No |
| `SKILL_SCANNER_LLM_MODEL` | LLM model name | No |

Without LLM credentials, scanner runs pattern + static analysis only.

## Database Schema

```sql
-- V9__security_audit.sql
CREATE TABLE security_audit (
    id                    BIGSERIAL PRIMARY KEY,
    skill_version_id      BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    scan_id               VARCHAR(100),
    scanner_type          VARCHAR(50) NOT NULL DEFAULT 'skill-scanner',
    verdict               VARCHAR(20) NOT NULL,
    is_safe               BOOLEAN NOT NULL,
    max_severity          VARCHAR(20),
    findings_count        INT NOT NULL DEFAULT 0,
    findings              JSONB NOT NULL DEFAULT '[]'::jsonb,
    scan_duration_seconds DOUBLE PRECISION,
    scanned_at            TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

## Publish Flow with Scanner

1. User publishes skill → `SkillPublishService.publishFromEntries()`
2. If `securityScanService.isEnabled()`:
   - Save files to `/tmp/skillhub-scans/{versionId}/` (local mode) or `.zip` (upload mode)
   - Create `SecurityAudit` record (initial verdict: SUSPICIOUS)
   - Publish `ScanTask` to Redis Stream
   - Set `SkillVersion.status = SCANNING`
3. `ScanTaskConsumer` picks up the task:
   - Call scanner API via `SkillScannerAdapter`
   - `SecurityScanService.processScanResult()` updates audit record, sets status → PENDING_REVIEW
   - Publishes `ScanCompletedEvent`
4. `ScanCompletedEventListener` creates `ReviewTask` (in new transaction, after commit)
5. If scanner disabled: skip scan, create `ReviewTask` directly

## SkillVersion Status Flow

```
DRAFT → SCANNING → PENDING_REVIEW → PUBLISHED
                 ↘ SCAN_FAILED → PENDING_REVIEW (via markFailed + ReviewTask)
```

## Retry & Error Handling

- `AbstractStreamConsumer` retries up to 3 times via re-publishing to the same stream with incremented `retryCount`
- On final failure: `ScanTaskConsumer.markFailed()` sets status to `SCAN_FAILED` and creates a `ReviewTask` for manual review
- Temp files are cleaned up in both success and failure paths

## Verification

```bash
# Health check
curl http://localhost:8000/health

# List analyzers
curl http://localhost:8000/analyzers

# Run verification script
./scripts/verify-scanner.sh
```
