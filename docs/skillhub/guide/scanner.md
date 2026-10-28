# 配置与查看安全扫描

## 作者和审核员怎么查看

发布后在技能详情或审核页查看本次版本的扫描状态、报告与发现项。先核对版本，再查看严重级别、受影响文件和说明；不能把旧版本报告用于新版本。

- `SCANNING`：尚未完成，普通提审版本不能据此批准。
- `SCAN_FAILED`：执行失败，需要查看原因并重试，不等于判定有害。
- 已完成且有发现项：根据风险与策略处理，不等于执行失败；人工审核仍需判断。
- 未启用或没有报告：不是“零风险”。PRIVATE 路径可在禁用扫描时上传；普通 PUBLIC / NAMESPACE_ONLY 发布要求扫描启用。

### 运维排查顺序

1. 确认后端实际使用的 Scanner 地址和模式。下表是应用默认值；release Compose 将模式设为 `upload`，不能假设容器使用宿主机的 `localhost`。
2. 从后端可达的网络检查 Scanner 的 `/health`，再检查实际扫描请求和返回。健康成功不代表某个包扫描成功。
3. 对照技能版本、请求时间及后端/Scanner 日志，区分网络或服务暂时不可用与包分析错误。暂时不可用会保留待处理并重领，不能只因等待较久就认定失败。
4. 修复服务或配置后跟踪原任务。记录版本 ID、状态和错误信息供管理员定位；不要公开包内秘密或认证信息。


## 功能描述

SkillHub 内置了 **Skill Scanner** 安全扫描服务，在技能包发布时自动检测潜在的安全风险。这是保障企业内部技能包安全的重要防线。

启用扫描的上传会经过安全扫描，扫描结果会影响审核决策，帮助管理员快速判断技能包是否安全可靠。

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

在 `.env` 文件或环境变量中配置：

| 环境变量 | 说明 | 默认值 |
|----------|------|--------|
| `SKILLHUB_SECURITY_SCANNER_ENABLED` | 启用安全扫描 | `true` |
| `SKILLHUB_SECURITY_SCANNER_URL` | Scanner 服务地址 | `http://localhost:8000` |
| `SKILLHUB_SECURITY_SCANNER_MODE` | 扫描模式（local / upload） | `local` |
| `SKILLHUB_SCANNER_POLICY_PRESET` | 策略预设 | `balanced` |
| `SKILLHUB_SCANNER_FAIL_ON_SEVERITY` | 自动阻止的严重级别 | `high` |

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

使用一键部署时，Scanner 服务默认启用。如果不需要安全扫描，可以通过 `--no-scanner` 参数禁用：

```bash
# 部署时禁用 Scanner
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --no-scanner
```

## 注意事项

> **异步上传不等于发布放行**：普通公开及空间内发布要求扫描已启用，扫描未完成时不能视为已发布。SCAN_FAILED 是执行失败；扫描发现风险与执行失败须分别处理。扫描通过也不等于人工审核通过。

- **扫描耗时**：根据技能包大小和启用的引擎数量，扫描可能需要几秒到几分钟
- **LLM 分析成本**：启用 LLM 分析会产生 API 调用费用，建议在生产环境中评估成本
- **策略调优**：`balanced` 策略适合大多数场景，企业可以根据安全需求自定义策略
- **健康检查**：通过 `GET http://localhost:8000/health` 检查 Scanner 服务状态
