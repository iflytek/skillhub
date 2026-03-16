# skill-scanner API Reference

> Based on OpenAPI 3.1.0 spec, skill-scanner v2.0.4

## Overview

skill-scanner is a security scanning service for agent skill packages. It analyzes skill code for security vulnerabilities, malicious patterns, and policy violations using static analysis, pattern matching, and optional LLM/behavioral analysis.

SkillHub integrates with skill-scanner to gate skill publishing behind automated security review.

## Base URL

```
http://localhost:8000
```

Default port is `8000`. Configurable via `SKILLHUB_SECURITY_SCANNER_URL`.

---

## Endpoints

### GET `/` — Service Info

Returns service metadata.

**Response** `200`:
```json
{
  "service": "skill-scanner",
  "version": "2.0.4",
  "description": "Security scanner for agent skill packages"
}
```

---

### GET `/health` — Health Check

**Response** `200`:
```json
{
  "status": "healthy",
  "analyzers": ["static", "pattern", "manifest"],
  "version": "2.0.4"
}
```

---

### GET `/analyzers` — List Analyzers

Returns available analyzer modules and their status.

**Response** `200`:
```json
{
  "analyzers": [
    {
      "name": "static",
      "enabled": true,
      "description": "Static code analysis"
    },
    {
      "name": "pattern",
      "enabled": true,
      "description": "Pattern-based threat detection"
    },
    {
      "name": "manifest",
      "enabled": true,
      "description": "Manifest and metadata validation"
    },
    {
      "name": "behavioral",
      "enabled": false,
      "description": "Behavioral analysis (requires sandbox)"
    },
    {
      "name": "llm",
      "enabled": false,
      "description": "LLM-based code review"
    }
  ]
}
```

---

### POST `/scan` — Scan Local Directory

Scans a skill package from a local filesystem path. **Preferred mode** when skill-scanner and SkillHub share the same filesystem (e.g., same Docker image).

**Request Body** (`application/json`):
```json
{
  "skill_directory": "/tmp/skillhub-scans/12345",
  "policy": "default",
  "use_llm": false,
  "use_behavioral": false
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `skill_directory` | string | yes | — | Absolute path to the skill package directory |
| `policy` | string | no | `"default"` | Scan policy (`default`, `strict`, `permissive`) |
| `use_llm` | boolean | no | `false` | Enable LLM-based code review |
| `use_behavioral` | boolean | no | `false` | Enable behavioral sandbox analysis |

**Response** `200`:
```json
{
  "scan_id": "a1b2c3d4",
  "is_safe": true,
  "max_severity": null,
  "findings_count": 0,
  "findings": [],
  "scan_duration_seconds": 1.23
}
```

**Errors**:
- `400` — Invalid or missing `skill_directory`
- `403` — Path traversal detected (directory outside allowed roots)
- `404` — Directory not found

**Security**: The scanner validates that `skill_directory` is within allowed base paths to prevent path traversal attacks.

---

### POST `/scan-upload` — Scan Uploaded File

Scans a skill package uploaded as a ZIP/tar.gz file via multipart form.

**Request Body** (`multipart/form-data`):

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `file` | file | yes | Skill package archive (`.zip`, `.tar.gz`) |
| `policy` | string | no | Scan policy |
| `use_llm` | boolean | no | Enable LLM analysis |
| `use_behavioral` | boolean | no | Enable behavioral analysis |

**Response** `200`: Same format as `/scan`.

**Errors**:
- `400` — No file provided or unsupported format
- `413` — File exceeds upload size limit (default 100MB)

---

### POST `/scan-batch` — Start Batch Scan

Starts an asynchronous batch scan of multiple skill packages. Returns immediately with a `scan_id` for polling.

**Request Body** (`application/json`):
```json
{
  "skills": [
    { "skill_directory": "/path/to/skill-a" },
    { "skill_directory": "/path/to/skill-b" }
  ],
  "policy": "default"
}
```

**Response** `202`:
```json
{
  "scan_id": "batch-xyz-123",
  "status": "running",
  "total": 2,
  "completed": 0
}
```

---

### GET `/scan-batch/{scan_id}` — Poll Batch Status

Polls the status of a batch scan.

**Response** `200`:
```json
{
  "scan_id": "batch-xyz-123",
  "status": "completed",
  "total": 2,
  "completed": 2,
  "results": [
    { "skill_directory": "/path/to/skill-a", "is_safe": true, "findings_count": 0 },
    { "skill_directory": "/path/to/skill-b", "is_safe": false, "findings_count": 3 }
  ]
}
```

| Status | Description |
|--------|-------------|
| `running` | Scan in progress |
| `completed` | All scans finished |
| `failed` | Batch scan encountered an error |

---

## Common Response: Scan Result

All scan endpoints return the same result structure:

```json
{
  "scan_id": "string",
  "is_safe": true,
  "max_severity": "CRITICAL | HIGH | MEDIUM | LOW | null",
  "findings_count": 0,
  "findings": [
    {
      "rule_id": "STATIC-001",
      "severity": "HIGH",
      "category": "code-execution",
      "title": "Dynamic code execution detected",
      "message": "Use of eval() can execute arbitrary code",
      "location": {
        "file": "src/handler.py",
        "line": 42,
        "column": 5
      },
      "code_snippet": "eval(user_input)",
      "remediation": "Avoid eval(). Use ast.literal_eval() for safe parsing.",
      "analyzer": "static",
      "metadata": {}
    }
  ],
  "scan_duration_seconds": 1.23
}
```

## Security Controls

- **Path validation**: `/scan` validates `skill_directory` against allowed base paths to prevent directory traversal
- **Upload limits**: `/scan-upload` enforces max file size (configurable, default 100MB)
- **API Key** (optional): Set `X-API-Key` header when `SCANNER_API_KEY` is configured on the scanner

## Deployment

In SkillHub's Docker deployment, skill-scanner runs in the same container and shares the filesystem. This makes `/scan` (local directory mode) the preferred integration path — no file upload overhead.

Configure via `application.yml`:
```yaml
skillhub:
  security:
    scanner:
      enabled: true
      mode: local          # local (default) | upload
      base-url: http://localhost:8000
```

- `mode: local` — Uses `/scan` endpoint, passes directory path directly
- `mode: upload` — Uses `/scan-upload` endpoint, uploads ZIP via multipart
