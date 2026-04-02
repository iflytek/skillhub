# Skill Scanner Security Scanning

## Overview

SkillHub includes a built-in **Skill Scanner** security scanning service that automatically detects potential security risks when skill packages are published. This serves as a critical line of defense for ensuring the security of skill packages within an enterprise.

Every skill package undergoes a security scan after publication. The scan results inform review decisions and help administrators quickly assess whether a skill package is safe and reliable.

**Key Features**:

- **Automatic Triggering**: Security scanning is triggered automatically after a skill package is published, requiring no manual intervention
- **Multi-Engine Analysis**: Supports multiple analysis engines including behavioral analysis, LLM analysis, and metadata analysis
- **Configurable Policies**: Includes a built-in `balanced` policy preset with support for custom scanning policies
- **Severity Threshold**: Configurable severity level at which publication is automatically blocked
- **Scan Reports**: Detailed scan results are displayed on the skill package detail page

**Analysis Engines**:

| Engine | Description | Default Status |
|------|------|----------|
| **Metadata Analysis** | Checks package structure, file types, sizes, etc. | Enabled |
| **Behavioral Analysis** | Analyzes code behavior patterns to detect malicious operations | Optional |
| **LLM Analysis** | Uses large language models to analyze code security | Optional |
| **AI Defense** | Cisco AI Defense integration | Optional |
| **VirusTotal** | VirusTotal virus scanning | Optional |

## Use Cases

**Case 1: Automatic Scanning on Publish**

After a developer publishes a skill package, the Scanner automatically runs a scan in the background with no additional steps required.

**Case 2: Administrator Reviews Scan Report**

When reviewing a skill package, an administrator can view the scan report to help make review decisions.

**Case 3: Custom Scanning Policies**

Enterprise administrators can configure scanning policies and severity thresholds based on their security requirements.

## Workflow

```
Developer publishes a skill package
    |
SkillHub backend receives the upload
    |
Security scan is triggered (via Redis Stream)
    |
Skill Scanner performs multi-engine analysis
    |
Scan results are written to the database
    |
Skill package detail page displays the scan report
    |
Administrator reviews with scan results in consideration
```

## Configuration

### Basic Configuration

Configure via `.env` file or environment variables:

| Environment Variable | Description | Default |
|----------|------|--------|
| `SKILLHUB_SECURITY_SCANNER_ENABLED` | Enable security scanning | `true` |
| `SKILLHUB_SECURITY_SCANNER_URL` | Scanner service URL | `http://localhost:8000` |
| `SKILLHUB_SECURITY_SCANNER_MODE` | Scan mode (local / upload) | `local` |
| `SKILLHUB_SCANNER_POLICY_PRESET` | Policy preset | `balanced` |
| `SKILLHUB_SCANNER_FAIL_ON_SEVERITY` | Severity level for automatic blocking | `high` |

### LLM Analysis Configuration (Optional)

Enabling the LLM analysis engine can improve the accuracy of security detection:

| Environment Variable | Description | Default |
|----------|------|--------|
| `SKILLHUB_SCANNER_USE_LLM` | Enable LLM analysis | `false` |
| `SKILLHUB_SCANNER_LLM_PROVIDER` | LLM provider (anthropic / openai / azure) | `anthropic` |
| `SKILL_SCANNER_LLM_API_KEY` | LLM API key | - |

### Deployment Notes

When using the one-click deployment, the Scanner service is enabled by default. If security scanning is not needed, it can be disabled with the `--no-scanner` flag:

```bash
# Disable Scanner during deployment
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --no-scanner
```

## Notes

> **Scanning Does Not Block Publishing**: Security scanning runs asynchronously and does not block the skill package upload process. Scan results are updated on the skill package detail page once completed.

- **Scan Duration**: Depending on the skill package size and the number of enabled engines, scanning may take from a few seconds to several minutes
- **LLM Analysis Cost**: Enabling LLM analysis incurs API call charges; it is recommended to evaluate costs in production environments
- **Policy Tuning**: The `balanced` policy is suitable for most scenarios; enterprises can customize policies based on their security needs
- **Health Check**: Check the Scanner service status via `GET http://localhost:8000/health`
