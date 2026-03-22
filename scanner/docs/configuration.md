# Scanner 配置说明

## 概述

本文档详细说明 SkillHub Scanner 的所有配置项，包括基础配置、分析器配置、策略配置和运维配置。

## 配置文件位置

- **开发环境**：`server/skillhub-app/src/main/resources/application-local.yml`
- **测试环境**：`server/skillhub-app/src/main/resources/application-test.yml`
- **生产环境**：`server/skillhub-app/src/main/resources/application.yml`
- **Kubernetes**：`deploy/k8s/configmap.yaml` 和 `deploy/k8s/secret.yaml`

## 完整配置示例

```yaml
skillhub:
  security:
    scanner:
      # 基础配置
      enabled: ${SKILLHUB_SECURITY_SCANNER_ENABLED:false}
      base-url: ${SKILLHUB_SECURITY_SCANNER_URL:http://localhost:8000}
      mode: ${SKILLHUB_SECURITY_SCANNER_MODE:local}

      # 分析器配置
      analyzers:
        behavioral: ${SKILLHUB_SCANNER_USE_BEHAVIORAL:false}
        llm: ${SKILLHUB_SCANNER_USE_LLM:false}
        llm-provider: ${SKILLHUB_SCANNER_LLM_PROVIDER:anthropic}
        llm-consensus-runs: ${SKILLHUB_SCANNER_LLM_CONSENSUS_RUNS:3}
        meta: ${SKILLHUB_SCANNER_USE_META:true}
        ai-defense: ${SKILLHUB_SCANNER_USE_AI_DEFENSE:false}
        ai-defense-api-key: ${SKILLHUB_SCANNER_AI_DEFENSE_API_KEY:}
        virus-total: ${SKILLHUB_SCANNER_USE_VIRUS_TOTAL:false}
        trigger: ${SKILLHUB_SCANNER_USE_TRIGGER:false}

      # 策略配置
      policy:
        preset: ${SKILLHUB_SCANNER_POLICY_PRESET:balanced}
        custom-policy-path: ${SKILLHUB_SCANNER_CUSTOM_POLICY_PATH:}
        fail-on-severity: ${SKILLHUB_SCANNER_FAIL_ON_SEVERITY:high}
```

## 配置项详解

### 1. 基础配置

#### `enabled`

- **类型**：Boolean
- **默认值**：`false`
- **环境变量**：`SKILLHUB_SECURITY_SCANNER_ENABLED`
- **说明**：是否启用安全扫描功能
- **影响**：
  - `true`：技能包发布时会触发安全扫描
  - `false`：跳过安全扫描，直接进入审核流程

**示例**：

```yaml
# 开发环境：禁用扫描
enabled: false

# 生产环境：启用扫描
enabled: true
```

---

#### `base-url`

- **类型**：String (URL)
- **默认值**：`http://localhost:8000`
- **环境变量**：`SKILLHUB_SECURITY_SCANNER_URL`
- **说明**：Scanner 服务的基础 URL
- **格式**：`http(s)://host:port`

**示例**：

```yaml
# 本地开发
base-url: http://localhost:8000

# Kubernetes 内部服务
base-url: http://skill-scanner:8000

# 外部服务
base-url: https://scanner.example.com
```

---

#### `mode`

- **类型**：String (Enum)
- **可选值**：`local` | `upload`
- **默认值**：`local`
- **环境变量**：`SKILLHUB_SECURITY_SCANNER_MODE`
- **说明**：扫描模式
  - `local`：Scanner 直接访问本地文件系统（适用于 Scanner 和 SkillHub 在同一主机）
  - `upload`：通过 HTTP 上传 ZIP 文件（适用于 Scanner 和 SkillHub 分离部署）

**示例**：

```yaml
# Docker Compose 环境（共享卷）
mode: local

# Kubernetes 环境（独立 Pod）
mode: upload
```

---

### 2. 分析器配置

#### `analyzers.behavioral`

- **类型**：Boolean
- **默认值**：`false`
- **环境变量**：`SKILLHUB_SCANNER_USE_BEHAVIORAL`
- **说明**：是否启用行为分析引擎
- **功能**：检测可疑的运行时行为（如文件系统访问、网络请求等）

---

#### `analyzers.llm`

- **类型**：Boolean
- **默认值**：`false`
- **环境变量**：`SKILLHUB_SCANNER_USE_LLM`
- **说明**：是否启用 LLM 分析引擎
- **功能**：使用大语言模型进行代码语义分析
- **依赖**：需要配置 `llm-provider`

---

#### `analyzers.llm-provider`

- **类型**：String (Enum)
- **可选值**：`anthropic` | `openai` | `azure`
- **默认值**：`anthropic`
- **环境变量**：`SKILLHUB_SCANNER_LLM_PROVIDER`
- **说明**：LLM 提供商
- **依赖**：需要在 Scanner 服务中配置对应的 API Key

**示例**：

```yaml
# 使用 Anthropic Claude
llm-provider: anthropic

# 使用 OpenAI GPT
llm-provider: openai

# 使用 Azure OpenAI
llm-provider: azure
```

---

#### `analyzers.llm-consensus-runs`

- **类型**：Integer
- **默认值**：`3`
- **范围**：`1-10`
- **环境变量**：`SKILLHUB_SCANNER_LLM_CONSENSUS_RUNS`
- **说明**：LLM 共识运行次数（多次运行取共识结果，提高准确性）
- **性能影响**：值越大，扫描时间越长，但准确性越高

---

#### `analyzers.meta`

- **类型**：Boolean
- **默认值**：`true`
- **环境变量**：`SKILLHUB_SCANNER_USE_META`
- **说明**：是否启用元数据分析引擎
- **功能**：检查 package.json、依赖版本、许可证等元数据

---

#### `analyzers.ai-defense`

- **类型**：Boolean
- **默认值**：`false`
- **环境变量**：`SKILLHUB_SCANNER_USE_AI_DEFENSE`
- **说明**：是否启用 AI Defense 引擎
- **功能**：使用 AI Defense API 进行高级威胁检测
- **依赖**：需要配置 `ai-defense-api-key`

---

#### `analyzers.ai-defense-api-key`

- **类型**：String (Secret)
- **默认值**：空字符串
- **环境变量**：`SKILLHUB_SCANNER_AI_DEFENSE_API_KEY`
- **说明**：AI Defense API Key
- **安全**：应通过 Kubernetes Secret 或环境变量注入，不要硬编码

---

#### `analyzers.virus-total`

- **类型**：Boolean
- **默认值**：`false`
- **环境变量**：`SKILLHUB_SCANNER_USE_VIRUS_TOTAL`
- **说明**：是否启用 VirusTotal 引擎
- **功能**：使用 VirusTotal API 检测已知恶意文件

---

#### `analyzers.trigger`

- **类型**：Boolean
- **默认值**：`false`
- **环境变量**：`SKILLHUB_SCANNER_USE_TRIGGER`
- **说明**：是否启用触发器分析引擎
- **功能**：检测可疑的触发器模式（如定时任务、事件监听等）

---

### 3. 策略配置

#### `policy.preset`

- **类型**：String (Enum)
- **可选值**：`strict` | `balanced` | `permissive`
- **默认值**：`balanced`
- **环境变量**：`SKILLHUB_SCANNER_POLICY_PRESET`
- **说明**：安全策略预设
  - `strict`：严格模式，任何可疑行为都会标记为不安全
  - `balanced`：平衡模式，只标记高风险行为
  - `permissive`：宽松模式，只标记明确的恶意行为

**示例**：

```yaml
# 生产环境：使用严格模式
preset: strict

# 开发环境：使用宽松模式
preset: permissive
```

---

#### `policy.custom-policy-path`

- **类型**：String (File Path)
- **默认值**：空字符串
- **环境变量**：`SKILLHUB_SCANNER_CUSTOM_POLICY_PATH`
- **说明**：自定义策略文件路径（覆盖 preset）
- **格式**：YAML 文件

**示例**：

```yaml
# 使用自定义策略
custom-policy-path: /etc/skillhub/scanner-policy.yaml
```

---

#### `policy.fail-on-severity`

- **类型**：String (Enum)
- **可选值**：`critical` | `high` | `medium` | `low`
- **默认值**：`high`
- **环境变量**：`SKILLHUB_SCANNER_FAIL_ON_SEVERITY`
- **说明**：扫描失败的严重级别门槛
  - `critical`：只有发现 critical 级别的问题才标记为不安全
  - `high`：发现 high 或 critical 级别的问题标记为不安全
  - `medium`：发现 medium、high 或 critical 级别的问题标记为不安全
  - `low`：发现任何级别的问题都标记为不安全

**示例**：

```yaml
# 生产环境：high 及以上标记为不安全
fail-on-severity: high

# 测试环境：只有 critical 标记为不安全
fail-on-severity: critical
```

---

## 环境变量配置

### Docker Compose

```yaml
# docker-compose.yml
services:
  skillhub-backend:
    environment:
      - SKILLHUB_SECURITY_SCANNER_ENABLED=true
      - SKILLHUB_SECURITY_SCANNER_URL=http://skill-scanner:8000
      - SKILLHUB_SECURITY_SCANNER_MODE=local
      - SKILLHUB_SCANNER_USE_BEHAVIORAL=false
      - SKILLHUB_SCANNER_USE_LLM=false
      - SKILLHUB_SCANNER_USE_META=true
      - SKILLHUB_SCANNER_POLICY_PRESET=balanced
      - SKILLHUB_SCANNER_FAIL_ON_SEVERITY=high
```

### Kubernetes ConfigMap

```yaml
# deploy/k8s/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: skillhub-config
data:
  SKILLHUB_SECURITY_SCANNER_ENABLED: "true"
  SKILLHUB_SECURITY_SCANNER_URL: "http://skill-scanner:8000"
  SKILLHUB_SECURITY_SCANNER_MODE: "upload"
  SKILLHUB_SCANNER_USE_BEHAVIORAL: "false"
  SKILLHUB_SCANNER_USE_LLM: "false"
  SKILLHUB_SCANNER_USE_META: "true"
  SKILLHUB_SCANNER_POLICY_PRESET: "balanced"
  SKILLHUB_SCANNER_FAIL_ON_SEVERITY: "high"
```

### Kubernetes Secret

```yaml
# deploy/k8s/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: skillhub-secrets
type: Opaque
stringData:
  SKILLHUB_SCANNER_AI_DEFENSE_API_KEY: "your-api-key-here"
```

---

## 配置最佳实践

### 1. 开发环境配置

```yaml
skillhub:
  security:
    scanner:
      enabled: false  # 开发时禁用扫描，加快迭代速度
      base-url: http://localhost:8000
      mode: local
      analyzers:
        meta: true  # 只启用元数据分析
      policy:
        preset: permissive  # 使用宽松策略
        fail-on-severity: critical
```

### 2. 测试环境配置

```yaml
skillhub:
  security:
    scanner:
      enabled: true  # 测试环境启用扫描
      base-url: http://skill-scanner:8000
      mode: local
      analyzers:
        behavioral: true
        meta: true
        llm: false  # LLM 分析较慢，测试环境可选
      policy:
        preset: balanced
        fail-on-severity: high
```

### 3. 生产环境配置

```yaml
skillhub:
  security:
    scanner:
      enabled: true  # 生产环境必须启用扫描
      base-url: http://skill-scanner:8000
      mode: upload  # 使用上传模式，更安全
      analyzers:
        behavioral: true
        llm: true  # 启用 LLM 分析，提高准确性
        llm-provider: anthropic
        llm-consensus-runs: 3
        meta: true
        ai-defense: true  # 启用高级威胁检测
        virus-total: true
        trigger: true
      policy:
        preset: strict  # 使用严格策略
        fail-on-severity: high
```

---

## 性能调优

### 扫描速度 vs 准确性

| 配置 | 扫描时间 | 准确性 | 适用场景 |
|-----|---------|-------|---------|
| 只启用 meta | ~5 秒 | 低 | 开发环境 |
| meta + behavioral | ~15 秒 | 中 | 测试环境 |
| meta + behavioral + llm | ~60 秒 | 高 | 生产环境 |
| 全部启用 | ~120 秒 | 最高 | 高安全要求 |

### 推荐配置

```yaml
# 快速扫描（开发环境）
analyzers:
  meta: true

# 标准扫描（测试环境）
analyzers:
  behavioral: true
  meta: true

# 深度扫描（生产环境）
analyzers:
  behavioral: true
  llm: true
  meta: true
  ai-defense: true
```

---

## 故障排查

### 问题 1：Scanner 连接失败

**检查配置**：

```bash
# 检查 base-url 是否正确
curl -f $SKILLHUB_SECURITY_SCANNER_URL/health

# 检查网络连通性
ping skill-scanner
```

### 问题 2：扫描超时

**调整配置**：

```yaml
# 减少 LLM 共识运行次数
analyzers:
  llm-consensus-runs: 1  # 从 3 降到 1

# 或禁用 LLM 分析
analyzers:
  llm: false
```

### 问题 3：扫描失败率高

**调整策略**：

```yaml
# 降低严重级别门槛
policy:
  fail-on-severity: critical  # 从 high 改为 critical

# 或使用宽松策略
policy:
  preset: permissive  # 从 strict 改为 permissive
```

---

## 相关文档

- [故障影响分析](./failure-impact-analysis.md)
- [运维监控指南](./monitoring-guide.md)
- [改进建议](./improvement-recommendations.md)
- [Scanner 服务文档](../README.md)
