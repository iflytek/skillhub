# Skill Scanner 安全扫描

## 功能描述

SkillHub 内置了 **Skill Scanner** 安全扫描服务，在技能包发布时自动检测潜在的安全风险。这是保障企业内部技能包安全的重要防线。

每个技能包在发布后都会经过安全扫描，扫描结果会影响审核决策，帮助管理员快速判断技能包是否安全可靠。

**核心特性**：

- **自动触发**：技能包发布后自动触发安全扫描，无需手动操作
- **多引擎分析**：支持行为分析、LLM 分析、元数据分析等多种引擎
- **策略可配**：内置 balanced 策略预设，支持自定义扫描策略
- **严重级别阈值**：可配置在哪个严重级别自动阻止发布
- **扫描报告**：详细的扫描结果展示在技能包详情页

**分析引擎**：

| 引擎 | 说明 | 默认状态 |
|------|------|----------|
| **元数据分析** | 检查包结构、文件类型、大小等 | 启用 |
| **行为分析** | 分析代码行为模式，检测恶意操作 | 可选 |
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

### 部署说明

使用一键部署时，Scanner 服务默认启用。如果不需要安全扫描，可以通过 `--no-scanner` 参数禁用：

```bash
# 部署时禁用 Scanner
curl -fsSL https://imageless.oss-cn-beijing.aliyuncs.com/runtime.sh | sh -s -- up --no-scanner
```

## 注意事项

> **扫描不阻塞发布**：安全扫描是异步执行的，不会阻塞技能包的上传流程。扫描结果会在完成后更新到技能包详情页。

- **扫描耗时**：根据技能包大小和启用的引擎数量，扫描可能需要几秒到几分钟
- **LLM 分析成本**：启用 LLM 分析会产生 API 调用费用，建议在生产环境中评估成本
- **策略调优**：`balanced` 策略适合大多数场景，企业可以根据安全需求自定义策略
- **健康检查**：通过 `GET http://localhost:8000/health` 检查 Scanner 服务状态
