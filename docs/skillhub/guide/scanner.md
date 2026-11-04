# 配置与查看安全扫描

本页说明技能作者、审核员如何判读版本扫描报告，以及部署方如何配置和诊断 Scanner。报告提供风险发现与执行证据，不代替人工审核，也不保证技能在 Agent 中的实际行为。头像机器审核属于另一条处理链路，参见[技能头像与审核](./skill-avatars.html)。

## 前置条件与职责

| 操作 | 执行主体 | 前置条件 | 说明 |
| --- | --- | --- | --- |
| 读取报告 | 已登录且有报告访问权限的账号 | 技能及目标版本可定位 | 技能详情或审核详情中核对版本；公开可见不代表匿名可读报告。 |
| 修订内容 | 技能作者 | 具有版本更新权限 | 根据发现项修改包，再沿发布流程提交，不修改历史报告。 |
| 审核决策 | 具有目标命名空间审核权限的账号 | 读取本次提交及扫描状态 | 扫描完成不等于批准。 |
| 配置与诊断 | 部署方 | 具有容器或集群操作权限 | 业务角色不自动授予主机操作权限。 |

![扫描执行、报告判读与人工审核之间的关系](/diagrams/scan-result-flow.svg)

*图：扫描任务产生报告；执行失败与风险发现分别处置，人工审核独立判断。*

## 判读本次版本报告

1. 在技能详情或审核详情选择目标版本。先记录版本与扫描时间，避免将旧报告用于新内容。
2. 检查 `failureReason` 和 `scannedAt`，区分任务未完成、执行失败和已生成结果。`SCAN_FAILED` 表示执行失败，不表示已认定内容有害。
3. 对已完成报告，结合 `verdict`、`isSafe`、`maxSeverity` 和 `findings` 阅读机器结果；逐项核对文件位置、说明与修复措施，不能只查看发现数量。
4. 作者修订问题后提交新版本；审核员依据本次内容与报告作出批准或拒绝决定。版本仍在 `SCANNING` 时不能按普通提审路径批准，具体流程见[审核技能与套装](./review.html)。

以下是报告字段的示例节选，不是真实实例结果；接口按技能和版本返回扫描报告列表。

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

| 机器结果 | 映射依据 | 处置依据 | 说明 |
| --- | --- | --- | --- |
| `SAFE` | Scanner 返回 `is_safe=true` | 仍须核对内容、依赖及适用场景 | 是机器判定，不是安全保证。 |
| `BLOCKED` | 非上述结果且最高级别为 `CRITICAL` | 重点核查发现项及危险操作 | 枚举名称不等于注册中心已自动拒绝审核。 |
| `DANGEROUS` | 非上述结果且最高级别为 `HIGH` | 核查风险证据与修订内容 | 不等于扫描执行失败。 |
| `SUSPICIOUS` | 其他非 `SAFE` 结果，包括级别缺失 | 阅读具体发现及报告完整性 | 不得因级别较低直接认定可发布。 |

映射与字段依据：[Scanner 适配器](https://github.com/iflytek/skillhub/blob/main/server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/scanner/SkillScannerAdapter.java)、[报告接口](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SecurityAuditController.java)和[扫描状态处理](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/security/SecurityScanService.java)。

## 配置扫描与确认生效

下表是本页涉及的应用绑定。变量必须实际传入注册中心容器；仅在 `.env.release` 增加清单未引用的键不会生效。Compose 修改后重新创建注册中心；Kubernetes 更新覆盖配置并滚动受影响 Deployment。完整配置以[应用配置](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/resources/application.yml)及[Scanner 配置说明](https://github.com/iflytek/skillhub/blob/main/scanner/docs/configuration.md)为准。

| 配置项 | 应用默认与部署覆盖 | 影响 | 说明 |
| --- | --- | --- | --- |
| `SKILLHUB_SECURITY_SCANNER_ENABLED` | 应用、release Compose、Kustomize、Helm 为 `true`；运行脚本 `--no-scanner` 为 `false` | 关闭后拒绝普通新非私有上传 | 生效需重建或滚动。 |
| `SKILLHUB_SECURITY_SCANNER_URL` | 应用 `http://localhost:8000`；Compose `http://skill-scanner:8000`；Kustomize `http://skillhub-scanner:8000`；Helm 按 release 生成 | 注册中心访问 Scanner 的地址 | 生效需重建或滚动。 |
| `SKILLHUB_SECURITY_SCANNER_MODE` | 应用 `local`；Compose、Kustomize、Helm 覆盖为 `upload` | `local` 需双方共享相同目录路径；`upload` 传包 | 生效需重建或滚动。 |
| `SKILLHUB_SCANNER_POLICY_PRESET` | `balanced`；可选 `strict`、`permissive` | 传给 Scanner 的分析策略 | 显式透传并重建后生效。 |
| `SKILLHUB_SCANNER_FAIL_ON_SEVERITY` | 配置声明为 `high` | 当前适配器未将此字段传入扫描选项 | 不得将其视为已实现的自动发布阻断保障。 |
| `SKILLHUB_SCANNER_USE_BEHAVIORAL` / `SKILLHUB_SCANNER_USE_META` | `true` / `false` | 行为与元数据分析开关 | 显式透传并重建注册中心。 |
| `SKILLHUB_SCANNER_USE_AI_DEFENSE` / `SKILLHUB_SCANNER_USE_VIRUSTOTAL` | 均为 `false` | 可选外部检测引擎 | 除开关外须配置相应服务凭据；重建受影响服务。 |
| `SKILLHUB_SCANNER_USE_LLM` / `SKILLHUB_SCANNER_LLM_PROVIDER` | `false` / `anthropic`；提供方可选 `openai`、`azure` | 开启外部模型分析 | 注册中心重建；Scanner 侧另配 `SKILL_SCANNER_LLM_API_KEY`，可配 `SKILL_SCANNER_LLM_BASE_URL`、`SKILL_SCANNER_LLM_MODEL`；密钥变更后重建对应服务。 |

策略选项依据[装配代码](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/java/com/iflytek/skillhub/config/SkillScannerConfig.java)与[扫描选项](https://github.com/iflytek/skillhub/blob/main/server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/scanner/ScanOptions.java)。LLM 分析可能向配置的外部服务发送内容并产生费用；启用前由部署方核对数据范围与服务条款，不公开密钥或完整环境输出。

扫描关闭时，普通公开（`PUBLIC`）和空间成员可见（`NAMESPACE_ONLY`）新上传返回 `error.security.scanner.required`。私有（`PRIVATE`）仍可上传，但停留在 `UPLOADED`，须另行确认发布。已发布私有技能扩大范围时，最新版本已有可复用报告（`taskId`、`scannedAt` 非空，`failureReason=null`、`isSafe=true`）可继续审核；否则扫描关闭时拒绝。依据[发布前置](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java)、[可见范围变更](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillVisibilityService.java)与[私有发布确认](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillReviewSubmitService.java)。

## 诊断连接与任务

Compose 命令在含 `.env.release`、`compose.release.yml` 的运行目录执行。Kustomize 使用清单默认名称；Helm 名称按 [Chart README](https://github.com/iflytek/skillhub/blob/main/charts/skillhub/README.md)核对，不得混用。探测仅输出固定允许值；异常配置脱敏，包含凭据或非保守 URL 形态时跳过。缺失变量取应用默认值，额外 Spring 覆盖须在受保护配置中核对。

| 参数 | 值 | 影响范围 | 说明 |
| --- | --- | --- | --- |
| `wget -T` | 5秒 | 本次健康请求阶段超时 | 不修改扫描任务超时。 |
| `wget -t` | 1次 | 本次请求尝试次数 | 不触发扫描。 |

Compose：

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

Kustomize：

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

标准结果如下；`health_exit=0` 仅表示健康端点可达。自定义地址显示 `custom_or_unrecognized_redacted`，不代表无效；普通自定义 HTTP(S)／IPv6 地址仍可探测。`skipped_*` 表示本次诊断跳过，不能据此判定产品配置有效或无效。

```text
# Compose
 enabled=true mode=upload scanner_host=skill-scanner:8000
 health_exit=0
# Kustomize
 enabled=true mode=upload scanner_host=skillhub-scanner:8000
 health_exit=0
```

随后以本版本的失败原因和时间关联日志；反馈前脱敏，不公开整份日志。

```bash
   # Compose，仍在本次运行目录
   docker compose --env-file .env.release -f compose.release.yml logs --since=10m --tail=100 server skill-scanner
   # Kustomize，使用清单默认名称
   kubectl -n skillhub logs deployment/skillhub-server -c server --since=10m --tail=100
   kubectl -n skillhub logs deployment/skillhub-scanner -c scanner --since=10m --tail=100
   ```

## 结果确认与失败处置

已生成报告应能定位目标版本、扫描时间及具体发现；普通非私有版本扫描执行完成后进入 `PENDING_REVIEW`，私有版本进入 `UPLOADED`。已发布版本补扫不改变其发布状态。上述结果均不等于人工已批准。

| 现象 | 最可能原因 | 定位手段 | 处置 |
| --- | --- | --- | --- |
| 非私有上传返回 `error.security.scanner.required` | 扫描关闭 | 探测显示 `enabled=false` | 恢复服务与开关；不要修改数据库状态绕过检查。 |
| 标准部署显示 `local` 或 `localhost` | 透传缺失或覆盖不正确 | 对照本页配置表与容器输出 | 修正配置并重建；不要将容器 localhost 当作宿主机。 |
| `health_exit` 非零 | 服务未就绪或网络/地址错误 | Compose 执行 `docker compose --env-file .env.release -f compose.release.yml ps skill-scanner`；Kustomize 执行 `kubectl -n skillhub get pods -l app.kubernetes.io/name=skillhub-scanner` | 恢复连接后跟踪原任务；不要删除数据库或持久卷。 |
| 健康可达但版本仍扫描或失败 | 执行错误、超时或恢复重试超限 | 本版本 `failureReason` 与上述日志 | 仅在页面提供重试且有权限时重试；不要重复上传制造新版本。 |
| 报告非 `SAFE` 但任务已完成 | 扫描发现风险 | 核对 `findings` 与文件位置 | 修订或提交审核证据；不要把 `BLOCKED` 枚举当成自动拒绝记录。 |

诊断依据：[Compose](https://github.com/iflytek/skillhub/blob/main/compose.release.yml)、[Kustomize](https://github.com/iflytek/skillhub/blob/main/deploy/k8s/base/backend-deployment.yaml)与[Server 镜像](https://github.com/iflytek/skillhub/blob/main/server/Dockerfile)。标准镜像提供 `sh`、`wget`。

## 边界与后续

扫描执行时长由包内容、分析引擎和外部依赖决定，本页不承诺固定完成时间。风险发现、执行失败、人工拒绝是不同结果；[审核实现](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/ReviewService.java)当前不会仅凭最高严重级别自动替代人工审核。完成本步骤后，作者在[发布与更新](./skill-publish.html)跟踪版本，审核员在[审核](./review.html)处理申请；部署方排障不得改写版本或审核记录。
