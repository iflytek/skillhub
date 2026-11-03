# 配置与查看安全扫描

## 作者和审核员怎么查看

发布后在技能详情或审核页查看本次版本的扫描状态、报告与发现项。先核对版本，再查看严重级别、受影响文件和说明；不能把旧版本报告用于新版本。

- `SCANNING`：尚未完成，普通提审版本不能据此批准。
- `SCAN_FAILED`：执行失败，需要查看原因并重试，不等于判定有害。
- 已完成且有发现项：根据风险与策略处理，不等于执行失败；人工审核仍需判断。
- 未启用或没有报告：不是“零风险”。PRIVATE 路径可在禁用扫描时上传；普通 PUBLIC / NAMESPACE_ONLY 发布要求扫描启用。

### 部署方排查顺序

以下命令由有本实例容器或目标命名空间操作权限的部署方执行；普通技能作者提供版本与报告信息，不要把业务角色当作主机操作权限。先选择实际部署路径。Compose 命令在包含本次 `.env.release` 与 `compose.release.yml` 的目录执行；Kubernetes 命令以 Kustomize 默认命名空间 `skillhub` 为例。Helm 实例先按 [Chart README](https://github.com/iflytek/skillhub/blob/main/charts/skillhub/README.md) 核对 Deployment 名称，不要混用两条部署路径。

下列探测参数只约束本次诊断请求，不修改后台扫描任务配置。

| 操作 | 示例值 | 影响范围 | 说明 |
| --- | --- | --- | --- |
| `wget -T` | 5秒 | DNS、连接和读取阶段超时 | 不代表整个扫描任务的超时。 |
| `wget -t` | 1次 | 本次健康请求的尝试次数 | 不触发技能扫描或修改任务状态。 |

1. 核对注册中心容器声明的开关和模式，再从同一容器检查默认 `/health`。命令只回显 `true` / `false`、`local` / `upload` 与三个官方默认地址的固定主机名；其他值使用固定脱敏标记，不回显原配置。包含用户信息、查询、fragment、空白或非保守 HTTP(S) 形态的 URL 会跳过探测；这只是诊断限制，不是注册中心的 URL 配置校验。缺失变量使用应用默认值；额外 Spring 配置与自定义健康路径须在实例受保护配置中核对。

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

2. 对照结果：标准 Compose 为 `enabled=true mode=upload scanner_host=skill-scanner:8000`；Kustomize 为 `enabled=true mode=upload scanner_host=skillhub-scanner:8000`；可达时显示 `health_exit=0`。这只证明健康端点可达，不能证明某个版本已扫描。`enabled=false` 时不做健康请求，按禁用后的发布限制处理。 `custom_or_unrecognized_redacted` 表示地址未在显示白名单中，需由部署方在受保护配置中核对，不能公开真实值；不含凭据的普通自定义 HTTP(S)／IPv6 地址仍可探测。`skipped_unrecognized_url`、`skipped_unrecognized_enabled` 或 `skipped_unrecognized_mode` 表示本次诊断已跳过，不代表产品判定该配置有效或无效。
3. 关联本次版本与日志。在技能详情的本版本扫描报告中读取状态和失败原因，再在同一时间范围的日志中查找该版本 ID；不要用旧版本报告解释新版本。以下命令只读取本地日志，反馈前须脱敏，不能直接公开整份日志。

   ```bash
   # Compose，仍在本次运行目录
   docker compose --env-file .env.release -f compose.release.yml logs --since=10m --tail=100 server skill-scanner
   # Kustomize，使用清单默认名称
   kubectl -n skillhub logs deployment/skillhub-server -c server --since=10m --tail=100
   kubectl -n skillhub logs deployment/skillhub-scanner -c scanner --since=10m --tail=100
   ```

4. 按下表处理后跟踪同一版本。临时不可达可进入重试/重领，持续失败也受实例重试与超时配置约束；不要仅凭等待时间断定“有害”，也不要重复上传来制造新版本。

| 现象 | 最可能原因 | 定位手段 | 检查与处理 |
| --- | --- | --- | --- |
| 非私有上传被拒绝，出现 `error.security.scanner.required` | 扫描开关关闭 | 上方容器命令显示 `enabled=false`；核对本次是否使用 `--no-scanner` | 由部署方恢复扫描服务及注册中心配置后重试；不要重复上传或改写审核状态绕过检查。 |
| 标准部署显示 `local` 或目标为 `localhost:8000` | 环境透传缺失或私有覆盖层改变默认配置 | 对照下方同格默认/覆盖值及本次 `server` 环境声明 | 修正 `SKILLHUB_SECURITY_SCANNER_MODE` / `SKILLHUB_SECURITY_SCANNER_URL` 的透传并重新创建受影响容器；不要把容器 localhost 当作宿主机。 |
| `health_exit` 非零 | Scanner 未就绪、DNS/网络或地址错误 | Compose 用 `docker compose --env-file .env.release -f compose.release.yml ps skill-scanner`；Kustomize 用 `kubectl -n skillhub get pods -l app.kubernetes.io/name=skillhub-scanner` | 先恢复服务与连通性，再检查原任务；不要先删除数据库、Redis 或持久卷。 |
| 健康可达但版本仍在 `SCANNING` 或变为 `SCAN_FAILED` | 请求未完成、扫描执行错误或恢复重试超限 | 读取本版本报告的失败原因，关联上方注册中心与 Scanner 日志 | 区分执行失败与发现风险；仅在版本页提供重试且有操作权限时重试，不要把健康响应当作扫描完成或人工批准。 |

诊断命令依据 [Compose 清单](https://github.com/iflytek/skillhub/blob/main/compose.release.yml)、[Kustomize 后端清单](https://github.com/iflytek/skillhub/blob/main/deploy/k8s/base/backend-deployment.yaml) 与 [Server 镜像](https://github.com/iflytek/skillhub/blob/main/server/Dockerfile)；所用 `sh`、`wget` 在标准 Server 镜像中提供。

## 功能描述

SkillHub 内置了 **Skill Scanner** 安全扫描服务，在技能包发布时自动检测潜在的安全风险。报告提供结构化发现与执行证据，不能据此承诺技能内容安全。

启用扫描的上传会经过安全扫描，扫描结果会影响审核决策，供有权限的审核员结合使用场景判断；扫描完成不等于人工批准。

**核心特性**：

- **自动触发**：技能包发布后自动触发安全扫描，无需手动操作
- **多引擎分析**：支持行为分析、LLM 分析、元数据分析等多种引擎
- **策略可配**：内置 balanced 策略预设，支持自定义扫描策略
- **严重级别阈值**：可配置在哪个严重级别自动阻止发布
- **扫描报告**：详细的扫描结果展示在技能包详情页

**分析引擎**：

| 引擎 | 说明 | 默认状态 |
|------|------|----------|
| **元数据分析** | 元数据相关分析（不替代上传结构校验） | 默认关闭 |
| **行为分析** | 分析代码行为模式，检测恶意操作 | 默认启用 |
| **LLM 分析** | 使用大模型分析代码安全性 | 可选 |
| **AI Defense** | Cisco AI Defense 集成 | 可选 |
| **VirusTotal** | VirusTotal 病毒扫描 | 可选 |

## 使用场景

**场景一：发布时自动扫描**

开发者发布技能包后，Scanner 自动在后台运行扫描，无需额外操作。

**场景二：管理员查看扫描报告**

管理员在审核技能包时，可以查看扫描报告，帮助做出审核决策。

**场景三：自定义扫描策略**

企业管理员可以根据安全需求，配置扫描策略和严重级别阈值。

## 工作流程

```
开发者发布技能包
    ↓
SkillHub 后端接收上传
    ↓
触发安全扫描（通过 Redis Stream）
    ↓
Skill Scanner 执行多引擎分析
    ↓
扫描结果写入数据库
    ↓
技能包详情页展示扫描报告
    ↓
管理员结合扫描结果进行审核
```

## 配置说明

### 基础配置

下表列出本页排查所需的基本应用绑定；完整字段见表后的应用配置来源。环境变量必须实际传入 Server；仅在 `.env.release` 增加未被 Compose 引用的键不会生效。修改后重新创建 Server 容器；Kubernetes 须更新对应覆盖配置并滚动受影响 Deployment。

| 配置项 | 用途 | 应用默认与部署覆盖 | 检查与处理 |
| --- | --- | --- | --- |
| `SKILLHUB_SECURITY_SCANNER_ENABLED` | 启用扫描 | 应用、release Compose、Kustomize 和 Helm 默认 `true`；`--no-scanner` 将运行实例设为 `false` | `false` 时拒绝新的非私有上传；已有版本扩大范围的报告复用条件见下文。 |
| `SKILLHUB_SECURITY_SCANNER_URL` | Scanner 地址 | 应用 `http://localhost:8000`；release Compose 覆盖为 `http://skill-scanner:8000`；Kustomize 为 `http://skillhub-scanner:8000`；Helm 按 release 生成 Service 地址 | 用上方同容器健康请求验证，不能照搬宿主机 localhost。 |
| `SKILLHUB_SECURITY_SCANNER_MODE` | `local` / `upload` 协议 | 应用默认 `local`；release Compose、Kustomize 和 Helm 均覆盖为 `upload` | `upload` 上传包；`local` 要求双方在相同路径可见同一目录。 |
| `SKILLHUB_SCANNER_POLICY_PRESET` | 策略预设 | 应用默认 `balanced`；以实例透传为准 | 未被部署清单引用时须显式补入 Server 环境，修改后重建。 |
| `SKILLHUB_SCANNER_FAIL_ON_SEVERITY` | 阻断严重级别 | 应用默认 `high`；以实例透传为准 | 阈值属于扫描策略，不等于人工批准条件。 |

默认值与覆盖依据：[应用配置](https://github.com/iflytek/skillhub/blob/main/server/skillhub-app/src/main/resources/application.yml)、[Kustomize ConfigMap](https://github.com/iflytek/skillhub/blob/main/deploy/k8s/base/configmap.yaml) 和 [Helm ConfigMap](https://github.com/iflytek/skillhub/blob/main/charts/skillhub/templates/configmap.yaml)。

### LLM 分析配置（可选）

启用 LLM 分析引擎可以提高安全检测的准确性：

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `SKILLHUB_SCANNER_USE_LLM` | 启用 LLM 分析 | `false` |
| `SKILLHUB_SCANNER_LLM_PROVIDER` | LLM 提供商（anthropic / openai / azure） | `anthropic` |
| `SKILL_SCANNER_LLM_API_KEY` | LLM API 密钥 | - |
| `SKILL_SCANNER_LLM_BASE_URL` | 本地/自定义 LLM 服务地址 | - |
| `SKILL_SCANNER_LLM_MODEL` | LLM 模型名称 | - |

### 部署说明

使用运行脚本部署时，Scanner 默认启用。`--no-scanner` 不启动 Scanner，并关闭注册中心扫描开关。**禁用后，公开（`PUBLIC`）与空间成员可见（`NAMESPACE_ONLY`）的上传发布请求被拒绝**，返回 `error.security.scanner.required`。私有（`PRIVATE`）仍可上传，但上传后是 `UPLOADED`，还需有权限的操作者确认发布，不能把上传成功当成发布完成。

已发布私有技能申请扩大可见范围时，最新发布版本若已有可复用报告（`taskId`、`scannedAt` 非空，`failureReason=null` 且 `isSafe=true`），可沿用该报告进入审核；没有此报告且扫描关闭时同样拒绝。这里的机器结果不代表内容安全或人工已批准。

```bash
# 部署时禁用 Scanner
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --no-scanner
```

发布限制依据：[发布前置检查](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java)、[可见范围变更](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillVisibilityService.java) 与 [私有版本确认](https://github.com/iflytek/skillhub/blob/main/server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillReviewSubmitService.java)。

## 注意事项

> **异步上传不等于发布放行**：普通公开及空间成员可见新版本上传要求扫描已启用，扫描未完成时不能视为已发布。SCAN_FAILED 是执行失败；扫描发现风险与执行失败须分别处理。扫描通过也不等于人工审核通过。

- **扫描耗时**：根据技能包大小和启用的引擎数量，扫描可能需要几秒到几分钟
- **LLM 分析成本**：启用 LLM 分析会产生 API 调用费用，建议在生产环境中评估成本
- **策略调优**：`balanced` 策略适合大多数场景，企业可以根据安全需求自定义策略
- **健康检查**：使用上方[部署方排查顺序](#部署方排查顺序)从注册中心容器访问已配置的 Scanner；不要把宿主机或 Server 的 localhost 当作 Scanner 地址。
