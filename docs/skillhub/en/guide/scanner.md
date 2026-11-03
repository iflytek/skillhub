# Configure and Inspect Security Scans

## Reading a Report as an Author or Reviewer

Open the submitted version in skill details or review. Verify its version before inspecting severity, affected files and explanations; a prior version's report does not cover new files.

- `SCANNING`: unfinished; ordinary submissions cannot be approved on that basis.
- `SCAN_FAILED`: execution failed; investigate and retry. It does not mean the skill was found malicious.
- Completed with findings: assess findings and policy separately from execution failure; human review still applies.
- Disabled or missing report: not a zero-risk verdict. PRIVATE uploads can run without scanning, while ordinary PUBLIC / NAMESPACE_ONLY submissions require it enabled.

### Operator Troubleshooting Order

These commands require operator access to this installation’s containers or Kubernetes namespace. Skill authors provide the version/report information instead; a registry business role does not grant host access. Select the actual deployment path first. Run Compose commands from the directory containing this installation's `.env.release` and `compose.release.yml`. Kubernetes examples use the Kustomize default namespace `skillhub`. For Helm, identify the Deployment names using the [Chart README](https://github.com/iflytek/skillhub/blob/main/charts/skillhub/README.md); do not mix deployment paths.

These probe parameters affect only this diagnostic request, not background scan-task configuration.

| Operation | Example value | Scope | Explanation |
| --- | --- | --- | --- |
| `wget -T` | 5s | DNS, connection and read timeouts | This is not the overall scan-task timeout. |
| `wget -t` | 1 attempt | This health request | It does not trigger a scan or change task state. |

1. Inspect the registry container’s declared enable flag and mode, then check the default `/health` from the same container. The command prints only `true` / `false`, `local` / `upload`, and fixed host names for the three official default addresses. Other values use fixed redaction markers without echoing the original configuration. URLs containing user information, queries, fragments, whitespace or shapes outside conservative HTTP(S) handling skip the probe. This is a diagnostic limitation, not registry URL-configuration validation. Missing variables use application defaults; check extra Spring overrides and custom health paths in protected installation configuration.

   Compose:

   ```bash
   docker compose --env-file .env.release -f compose.release.yml exec -T server sh -c '
     enabled=${SKILLHUB_SECURITY_SCANNER_ENABLED:-true}
     mode=${SKILLHUB_SECURITY_SCANNER_MODE:-local}
     url=${SKILLHUB_SECURITY_SCANNER_URL:-http://localhost:8000}
     case "$enabled" in true|false) ;; *) enabled=unrecognized_redacted ;; esac
     case "$mode" in local|upload) ;; *) mode=unrecognized_redacted ;; esac
     case "$url" in
       http://localhost:8000|http://localhost:8000/) host=localhost:8000 ;;
       http://skill-scanner:8000|http://skill-scanner:8000/) host=skill-scanner:8000 ;;
       http://skillhub-scanner:8000|http://skillhub-scanner:8000/) host=skillhub-scanner:8000 ;;
       *) host=custom_or_unrecognized_redacted ;;
     esac
     probe=allowed
     case "$url" in http://?*|https://?*) ;; *) probe=skipped_unrecognized_url ;; esac
     case "$url" in *[!a-zA-Z0-9:/._~\[\]-]*|*@*|*\?*|*\#*|*\\*) probe=skipped_unrecognized_url ;; esac
     authority=${url#*://}; authority=${authority%%/*}
     case "$authority" in ""|:*|*:) probe=skipped_unrecognized_url ;; esac
     printf "enabled=%s mode=%s scanner_host=%s\n" "$enabled" "$mode" "$host"
     if [ "$enabled" = false ]; then printf "health=skipped_disabled\n"
     elif [ "$enabled" != true ]; then printf "health=skipped_unrecognized_enabled\n"
     elif [ "$mode" = unrecognized_redacted ]; then printf "health=skipped_unrecognized_mode\n"
     elif [ "$probe" != allowed ]; then printf "health=%s\n" "$probe"
     else
       wget -T 5 -t 1 -q -O /dev/null -- "${url%/}/health"; printf "health_exit=%s\n" "$?"
     fi
   '
   ```

   Kustomize:

   ```bash
   kubectl -n skillhub exec deployment/skillhub-server -c server -- sh -c '
     enabled=${SKILLHUB_SECURITY_SCANNER_ENABLED:-true}
     mode=${SKILLHUB_SECURITY_SCANNER_MODE:-local}
     url=${SKILLHUB_SECURITY_SCANNER_URL:-http://localhost:8000}
     case "$enabled" in true|false) ;; *) enabled=unrecognized_redacted ;; esac
     case "$mode" in local|upload) ;; *) mode=unrecognized_redacted ;; esac
     case "$url" in
       http://localhost:8000|http://localhost:8000/) host=localhost:8000 ;;
       http://skill-scanner:8000|http://skill-scanner:8000/) host=skill-scanner:8000 ;;
       http://skillhub-scanner:8000|http://skillhub-scanner:8000/) host=skillhub-scanner:8000 ;;
       *) host=custom_or_unrecognized_redacted ;;
     esac
     probe=allowed
     case "$url" in http://?*|https://?*) ;; *) probe=skipped_unrecognized_url ;; esac
     case "$url" in *[!a-zA-Z0-9:/._~\[\]-]*|*@*|*\?*|*\#*|*\\*) probe=skipped_unrecognized_url ;; esac
     authority=${url#*://}; authority=${authority%%/*}
     case "$authority" in ""|:*|*:) probe=skipped_unrecognized_url ;; esac
     printf "enabled=%s mode=%s scanner_host=%s\n" "$enabled" "$mode" "$host"
     if [ "$enabled" = false ]; then printf "health=skipped_disabled\n"
     elif [ "$enabled" != true ]; then printf "health=skipped_unrecognized_enabled\n"
     elif [ "$mode" = unrecognized_redacted ]; then printf "health=skipped_unrecognized_mode\n"
     elif [ "$probe" != allowed ]; then printf "health=%s\n" "$probe"
     else
       wget -T 5 -t 1 -q -O /dev/null -- "${url%/}/health"; printf "health_exit=%s\n" "$?"
     fi
   '
   ```

2. Compare the output: standard Compose reports `enabled=true mode=upload scanner_host=skill-scanner:8000`; Kustomize reports `enabled=true mode=upload scanner_host=skillhub-scanner:8000`. A reachable health endpoint reports `health_exit=0`. This does not establish that a particular version was scanned. With `enabled=false`, health is skipped and the publication restrictions below apply. `custom_or_unrecognized_redacted` means the address is outside the display allowlist: operators must inspect protected configuration without publishing its real value. Ordinary credential-free custom HTTP(S)/IPv6 addresses can still be probed. `skipped_unrecognized_url`, `skipped_unrecognized_enabled` and `skipped_unrecognized_mode` report a skipped diagnostic, not a product verdict that the configuration is valid or invalid.
3. Correlate the current version and logs. Read its scan report and failure reason in skill details, then find that version ID within the matching log time range. A prior version's report does not cover new content. These commands read local logs; redact credentials and package content before sharing, rather than publishing the complete output.

   ```bash
   # Compose, in this installation's runtime directory
   docker compose --env-file .env.release -f compose.release.yml logs --since=10m --tail=100 server skill-scanner
   # Kustomize default resource names
   kubectl -n skillhub logs deployment/skillhub-server -c server --since=10m --tail=100
   kubectl -n skillhub logs deployment/skillhub-scanner -c scanner --since=10m --tail=100
   ```

4. Follow the same version after applying the corresponding action below. Temporary unavailability may enter retry/reclaim handling; persistent failure is still bounded by the installation's retry and timeout configuration. Do not infer malicious content from elapsed time alone or upload again merely to create another version.

| Symptom | Likely cause | Where to inspect | Check and action |
| --- | --- | --- | --- |
| Non-private upload is rejected with `error.security.scanner.required` | Scanning is disabled | Container command reports `enabled=false`; check whether this installation used `--no-scanner` | Have the operator restore the Scanner and registry configuration before retrying; do not repeatedly upload or rewrite review state to bypass the check. |
| A standard deployment reports `local` or `localhost:8000` | Missing environment wiring or a private override | Compare the default/override values below with the installation's `server` environment | Correct the wiring of `SKILLHUB_SECURITY_SCANNER_MODE` / `SKILLHUB_SECURITY_SCANNER_URL` and recreate affected containers; do not treat container localhost as the host. |
| `health_exit` is nonzero | Scanner not ready, DNS/network or URL failure | Compose: `docker compose --env-file .env.release -f compose.release.yml ps skill-scanner`; Kustomize: `kubectl -n skillhub get pods -l app.kubernetes.io/name=skillhub-scanner` | Restore service/connectivity before following the original task; do not delete database, Redis or persistent volumes as a first response. |
| Health is reachable but the version remains `SCANNING` or becomes `SCAN_FAILED` | Request unfinished, execution error or recovery limits exceeded | Read this version's failure reason and correlate the registry/Scanner logs above | Separate execution failure from findings; retry only when offered for the version and authorized. Do not treat health as scan completion or human approval. |

Command sources: the [Compose manifest](https://github.com/iflytek/skillhub/blob/main/compose.release.yml), [Kustomize backend manifest](https://github.com/iflytek/skillhub/blob/main/deploy/k8s/base/backend-deployment.yaml), and [Server image](https://github.com/iflytek/skillhub/blob/main/server/Dockerfile). The standard Server image supplies the `sh` and `wget` used here.

## Overview

SkillHub includes a built-in **Skill Scanner** security scanning service that automatically detects potential security risks when skill packages are published. Reports provide structured findings and execution evidence, not a guarantee that skill content is safe.

Uploads with scanning enabled undergo a security scan. The scan results inform review decisions and support an authorized reviewer’s assessment of the intended use; completing a scan does not constitute human approval.

**Key Features**:

- **Automatic Triggering**: Security scanning is triggered automatically after a skill package is published, requiring no manual intervention
- **Multi-Engine Analysis**: Supports multiple analysis engines including behavioral analysis, LLM analysis, and metadata analysis
- **Configurable Policies**: Includes a built-in `balanced` policy preset with support for custom scanning policies
- **Severity Threshold**: Configurable severity level at which publication is automatically blocked
- **Scan Reports**: Detailed scan results are displayed on the skill package detail page

**Analysis Engines**:

| Engine | Description | Default Status |
|------|------|----------|
| **Metadata Analysis** | Metadata analysis, separate from upload structure validation | Disabled by default |
| **Behavioral Analysis** | Analyzes code behavior patterns to detect malicious operations | Enabled by default |
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

The table lists basic registry bindings used by these checks; the application configuration linked below contains the complete field set. Variables must reach the Server process; adding an unreferenced key to `.env.release` alone has no effect. Recreate Server containers after changes. On Kubernetes, update the relevant configuration overlay and roll affected Deployments.

| Configuration | Purpose | Application default and deployment override | Check and action |
| --- | --- | --- | --- |
| `SKILLHUB_SECURITY_SCANNER_ENABLED` | Enable scanning | Application, release Compose, Kustomize and Helm default to `true`; `--no-scanner` sets the runtime to `false` | New non-private uploads are rejected when false; report reuse for existing versions is described below. |
| `SKILLHUB_SECURITY_SCANNER_URL` | Scanner address | Application: `http://localhost:8000`; release Compose: `http://skill-scanner:8000`; Kustomize: `http://skillhub-scanner:8000`; Helm generates the Service address from the release | Check health from the same container; do not copy the host’s localhost address. |
| `SKILLHUB_SECURITY_SCANNER_MODE` | `local` / `upload` protocol | Application default: `local`; release Compose, Kustomize and Helm all override it with `upload` | `upload` sends the package; `local` requires the same directory at the same path on both sides. |
| `SKILLHUB_SCANNER_POLICY_PRESET` | Policy preset | Application default: `balanced`; effective wiring is installation-specific | Add the Server environment entry explicitly if absent from the manifest, then recreate it. |
| `SKILLHUB_SCANNER_FAIL_ON_SEVERITY` | Blocking severity | Application default: `high`; effective wiring is installation-specific | This threshold controls scanning policy, not the human approval decision. |

Defaults/overrides: [application configuration](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/resources/application.yml), [Kustomize ConfigMap](https://github.com/iflytek/skillhub/blob/main/deploy/k8s/base/configmap.yaml), and [Helm ConfigMap](https://github.com/iflytek/skillhub/blob/main/charts/skillhub/templates/configmap.yaml).

### LLM Analysis Configuration (Optional)

Enabling the LLM analysis engine can improve the accuracy of security detection:

| Environment Variable | Description | Default |
|----------|------|--------|
| `SKILLHUB_SCANNER_USE_LLM` | Enable LLM analysis | `false` |
| `SKILLHUB_SCANNER_LLM_PROVIDER` | LLM provider (anthropic / openai / azure) | `anthropic` |
| `SKILL_SCANNER_LLM_API_KEY` | LLM API key | - |
| `SKILL_SCANNER_LLM_BASE_URL` | Local/custom LLM service base URL | - |
| `SKILL_SCANNER_LLM_MODEL` | LLM model name | - |

### Deployment Notes

The runtime script enables Scanner by default. `--no-scanner` does not start Scanner and disables scanning in the registry. **PUBLIC and NAMESPACE_ONLY upload/publication requests are then rejected** with `error.security.scanner.required`. PRIVATE uploads remain available, but leave the version `UPLOADED` until an authorized operator confirms publication. Upload success is not publication completion.

A published private skill may still request wider visibility using a reusable report for its latest published version (`taskId` and `scannedAt` present, `failureReason=null`, and `isSafe=true`). Without that report, disabled scanning also rejects the visibility request. The machine result is not a content-safety guarantee or human approval.

```bash
# Disable Scanner during deployment
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --no-scanner
```

Publication restrictions: [publish preconditions](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java), [visibility changes](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillVisibilityService.java), and [private-version confirmation](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillReviewSubmitService.java).

## Notes

> **Asynchronous upload is not publication**: Ordinary public and namespace-only submissions require scanning to be enabled. An unfinished scan does not establish publication. SCAN_FAILED means execution failure; findings and execution failures require different handling. Completing a scan does not imply review approval.

- **Scan Duration**: Depending on the skill package size and the number of enabled engines, scanning may take from a few seconds to several minutes
- **LLM Analysis Cost**: Enabling LLM analysis incurs API call charges; it is recommended to evaluate costs in production environments
- **Policy Tuning**: The `balanced` policy is suitable for most scenarios; enterprises can customize policies based on their security needs
- **Health Check**: Follow the [operator troubleshooting order](#operator-troubleshooting-order) to reach the configured Scanner from the registry container. Do not assume that host or Server localhost identifies Scanner.
