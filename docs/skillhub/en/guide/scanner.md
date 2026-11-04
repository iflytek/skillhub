# Configure and Inspect Security Scans

This page explains how Skill authors and reviewers interpret version scan reports and how deployment operators configure and diagnose Scanner. Reports provide findings and execution evidence, not approval or a guarantee of behavior inside an Agent. Avatar moderation is a separate processing path; see [Skill Avatars and Moderation](./skill-avatars.html).

## Prerequisites and Responsibilities

| Action | Operator | Prerequisite | Notes |
| --- | --- | --- | --- |
| Read a report | A signed-in account authorized to view the report | Identify the Skill and target version | Check the version in Skill or review details; public visibility does not imply anonymous report access. |
| Revise content | Skill author | Permission to update versions | Revise the package and submit through publication; do not edit historical reports. |
| Make a review decision | An account authorized for the target namespace | Read the submitted content and scan state | Scan completion is not approval. |
| Configure and diagnose | Deployment operator | Container or cluster access | Business roles do not grant host access. |

![Relationship between scan execution, report interpretation, and human review](/diagrams/scan-result-flow-en.svg)

*Figure: A scan task produces a report. Execution failure and risk findings require different actions; human review remains an independent decision.*

## Interpret the Target Version Report

1. Select the target version in Skill or review details. Record the version and scan time before reading findings, so an older report is not applied to new content.
2. Check `failureReason` and `scannedAt` to distinguish pending work, execution failure, and a completed result. `SCAN_FAILED` denotes execution failure, not a finding that the content is harmful.
3. For completed reports, inspect `verdict`, `isSafe`, `maxSeverity`, and `findings`. Check each file location, explanation, and remediation rather than relying only on finding counts.
4. Authors submit a revised version after fixing issues. Reviewers approve or reject based on the submitted content and evidence. A version still in `SCANNING` cannot be approved through the ordinary submission path; see [Review Skills and Suites](./review.html).

The following is an illustrative report excerpt, not a result from a real instance. The API returns a list of reports for a Skill and version.

```json
{
  "verdict": "DANGEROUS",
  "isSafe": false,
  "maxSeverity": "HIGH",
  "findingsCount": 1,
  "findings": [{
    "ruleId": "EXAMPLE_RULE",
    "severity": "HIGH",
    "filePath": "scripts/example.py",
    "lineNumber": 12,
    "message": "Example finding",
    "remediation": "Review the indicated operation"
  }],
  "failureReason": null
}
```

| Machine verdict | Mapping rule | Decision basis | Notes |
| --- | --- | --- | --- |
| `SAFE` | Scanner returns `is_safe=true` | Still inspect content, dependencies, and intended usage | A machine classification, not a safety guarantee. |
| `BLOCKED` | Otherwise, maximum severity is `CRITICAL` | Investigate the findings and dangerous operations | The enum name does not mean the registry automatically rejected the review. |
| `DANGEROUS` | Otherwise, maximum severity is `HIGH` | Inspect risk evidence and revisions | Not an execution failure. |
| `SUSPICIOUS` | Other non-`SAFE` results, including missing severity | Inspect concrete findings and report completeness | Lower severity alone does not establish publication eligibility. |

Sources: [Scanner adapter](https://github.com/iflytek/skillhub/blob/main/server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/scanner/SkillScannerAdapter.java), [report API](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SecurityAuditController.java), and [scan state processing](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/security/SecurityScanService.java).

## Configure Scanning and Confirm Effective Values

The table lists application bindings used on this page. Variables must reach the registry container. Adding a key to `.env.release` does not apply it when Compose does not reference it. Recreate the registry after Compose changes; update overrides and roll affected Deployments for Kubernetes. The complete configuration follows [application configuration](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/resources/application.yml) and the [Scanner configuration guide (Chinese)](https://github.com/iflytek/skillhub/blob/main/scanner/docs/configuration.md).

| Setting | Application default and deployment overrides | Effect | Notes |
| --- | --- | --- | --- |
| `SKILLHUB_SECURITY_SCANNER_ENABLED` | Application, release Compose, Kustomize, and Helm: `true`; runtime script `--no-scanner`: `false` | Disabling rejects ordinary new non-private uploads | Recreate or roll to apply. |
| `SKILLHUB_SECURITY_SCANNER_URL` | Application: `http://localhost:8000`; Compose: `http://skill-scanner:8000`; Kustomize: `http://skillhub-scanner:8000`; Helm: generated from the release | Registry-to-Scanner address | Recreate or roll to apply. |
| `SKILLHUB_SECURITY_SCANNER_MODE` | Application: `local`; Compose, Kustomize, and Helm override: `upload` | `local` requires the same directory path on both sides; `upload` transfers the package | Recreate or roll to apply. |
| `SKILLHUB_SCANNER_POLICY_PRESET` | `balanced`; alternatives: `strict`, `permissive` | Analysis policy sent to Scanner | Explicitly pass through and recreate. |
| `SKILLHUB_SCANNER_FAIL_ON_SEVERITY` | Declared as `high` | The current adapter does not pass this field into scan options | Must not be treated as an implemented automatic publication-blocking guarantee. |
| `SKILLHUB_SCANNER_USE_BEHAVIORAL` / `SKILLHUB_SCANNER_USE_META` | `true` / `false` | Behavioral and metadata analyzers | Explicitly pass through and recreate the registry. |
| `SKILLHUB_SCANNER_USE_AI_DEFENSE` / `SKILLHUB_SCANNER_USE_VIRUSTOTAL` | Both `false` | Optional external detection engines | Configure the corresponding credentials as well as switches; recreate affected services. |
| `SKILLHUB_SCANNER_USE_LLM` / `SKILLHUB_SCANNER_LLM_PROVIDER` | `false` / `anthropic`; alternatives: `openai`, `azure` | Enable external model analysis | Recreate the registry; separately set Scanner-side `SKILL_SCANNER_LLM_API_KEY`, optionally `SKILL_SCANNER_LLM_BASE_URL` and `SKILL_SCANNER_LLM_MODEL`. Recreate the corresponding service after secret changes. |

Options follow the [assembly code](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/config/SkillScannerConfig.java) and [scan options](https://github.com/iflytek/skillhub/blob/main/server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/scanner/ScanOptions.java). LLM analysis can send content to the configured external service and incur charges. Deployment operators must verify data scope and service terms before enabling it; do not disclose secrets or complete environment dumps.

With scanning disabled, ordinary new public (`PUBLIC`) and namespace-member-visible (`NAMESPACE_ONLY`) uploads return `error.security.scanner.required`. Private (`PRIVATE`) uploads remain possible but stop at `UPLOADED` and require separate publication confirmation. A published private Skill can reuse its latest version report for visibility expansion when `taskId` and `scannedAt` are present, `failureReason=null`, and `isSafe=true`; otherwise disabled scanning rejects the request. Sources: [publication prerequisites](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java), [visibility changes](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillVisibilityService.java), and [private publication confirmation](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillReviewSubmitService.java).

## Diagnose Connectivity and Tasks

Run Compose commands in the directory containing `.env.release` and `compose.release.yml`. Kustomize examples use manifest defaults; verify Helm names against the [Chart README (Chinese)](https://github.com/iflytek/skillhub/blob/main/charts/skillhub/README.md) instead of mixing paths. Probes display only fixed allowed values. Unrecognized configuration is redacted; URLs with credentials or unsupported conservative shapes are skipped. Missing variables use application defaults; additional Spring overrides must be inspected in protected instance configuration.

| Parameter | Value | Scope | Notes |
| --- | --- | --- | --- |
| `wget -T` | 5 seconds | Stages of this health request | Does not change scan-task timeouts. |
| `wget -t` | 1 attempt | This health request | Does not trigger a scan. |

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

Expected standard output is shown below. `health_exit=0` proves only health-endpoint reachability. Custom addresses display `custom_or_unrecognized_redacted`, which does not imply an invalid address; ordinary custom HTTP(S)/IPv6 targets can still be probed. `skipped_*` means this diagnostic skipped a step, not that product configuration was accepted or rejected.

```text
# Compose
 enabled=true mode=upload scanner_host=skill-scanner:8000
 health_exit=0
# Kustomize
 enabled=true mode=upload scanner_host=skillhub-scanner:8000
 health_exit=0
```

Next, correlate the version's failure reason and time with logs. Redact before sharing; do not publish complete logs.

```bash
   # Compose, in this installation's runtime directory
   docker compose --env-file .env.release -f compose.release.yml logs --since=10m --tail=100 server skill-scanner
   # Kustomize default resource names
   kubectl -n skillhub logs deployment/skillhub-server -c server --since=10m --tail=100
   kubectl -n skillhub logs deployment/skillhub-scanner -c scanner --since=10m --tail=100
   ```

## Completion Criteria and Failure Handling

A completed report identifies the target version, scan time, and concrete findings. Completed ordinary non-private scans proceed to `PENDING_REVIEW`; private versions proceed to `UPLOADED`. Scanning an already published version does not change its publication state. None of these outcomes means human approval is complete.

| Symptom | Likely cause | Diagnosis | Action |
| --- | --- | --- | --- |
| Non-private upload returns `error.security.scanner.required` | Scanning disabled | Probe shows `enabled=false` | Restore the service and switch; do not modify database state to bypass checks. |
| Standard deployment reports `local` or `localhost` | Missing passthrough or incorrect override | Compare the table and container output | Correct configuration and recreate; do not confuse container localhost with the host. |
| Nonzero `health_exit` | Service not ready, network failure, or wrong address | Compose: `docker compose --env-file .env.release -f compose.release.yml ps skill-scanner`; Kustomize: `kubectl -n skillhub get pods -l app.kubernetes.io/name=skillhub-scanner` | Restore connectivity and track the original task; do not delete databases or persistent volumes. |
| Health reachable but version remains scanning or fails | Execution error, timeout, or exhausted recovery retries | Inspect this version's `failureReason` and the logs above | Retry only when the page provides the action and authorization permits it; do not repeatedly upload to create new versions. |
| Completed task has a non-`SAFE` report | Risk findings | Compare `findings` with indicated files | Revise content or provide review evidence; do not interpret `BLOCKED` as an automatic rejection record. |

Diagnostic sources: [Compose](https://github.com/iflytek/skillhub/blob/main/compose.release.yml), [Kustomize](https://github.com/iflytek/skillhub/blob/main/deploy/k8s/base/backend-deployment.yaml), and [Server image](https://github.com/iflytek/skillhub/blob/main/server/Dockerfile). The standard image provides `sh` and `wget`.

## Boundaries and Next Steps

Duration depends on package content, analysis engines, and external dependencies; this page promises no fixed completion time. Findings, execution failures, and human rejections are different outcomes. The [review implementation](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java) does not currently replace human review solely based on maximum severity. Authors continue tracking versions under [Publish and Update](./skill-publish.html); reviewers handle applications under [Review](./review.html). Deployment troubleshooting must not rewrite version or review records.
