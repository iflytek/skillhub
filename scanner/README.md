# Skill Scanner

本目录提供 Cisco skill-scanner 的本地 Docker 构建上下文，用于 `make dev-all` 开发流程。

开发流程会将 `cisco-ai-skill-scanner` 构建到本地容器中，并在 `http://localhost:8000` 上暴露服务。

## 快速开始

### 环境变量

Scanner 服务的可选环境变量：

- `SKILL_SCANNER_LLM_API_KEY` - LLM API 密钥
- `SKILL_SCANNER_LLM_BASE_URL` - LLM API 基础 URL
- `SKILL_SCANNER_LLM_MODEL` - LLM 模型名称

### 启动服务

```bash
# 启动所有服务（包括 Scanner）
make dev-all

# 检查 Scanner 服务状态
curl http://localhost:8000/health
```

## 文档

- **[配置说明](./docs/configuration.md)** - 详细的配置项说明和最佳实践
- **[故障影响分析](./docs/failure-impact-analysis.md)** - Scanner 接口故障时的影响分析
- **[运维监控指南](./docs/monitoring-guide.md)** - 监控指标、告警规则和故障排查
- **[改进建议](./docs/improvement-recommendations.md)** - 系统改进建议（待实施）

## 架构说明

Scanner 服务与 SkillHub 的集成架构：

```
SkillHub Backend
    ↓
SecurityScanService.triggerScan()
    ↓
Redis Stream (skillhub:scan:requests)
    ↓
ScanTaskConsumer
    ↓
SkillScannerAdapter
    ↓
SkillScannerService (HTTP Client)
    ↓
Cisco skill-scanner API
```

## 相关配置

SkillHub 后端的 Scanner 配置位于：

- `server/skillhub-app/src/main/resources/application.yml`
- `deploy/k8s/configmap.yaml`
- `deploy/k8s/secret.yaml`

详见 [配置说明](./docs/configuration.md)。
