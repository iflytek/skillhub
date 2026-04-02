# Kubernetes 部署指南

本文档说明如何在 Kubernetes 集群中部署 SkillHub。

## 前置条件

- Kubernetes 集群 (v1.24+)
- kubectl 已配置并连接到集群
- nginx ingress controller 已安装（可选，用于域名访问）
- 默认 StorageClass 已配置（用于 PVC）
- GitHub Container Registry 镜像拉取权限

## 目录结构

```
deploy/k8s/
├── base/                          # 基础配置（所有场景共用）
│   ├── kustomization.yaml
│   ├── configmap.yaml
│   ├── secret.yaml.example
│   ├── services.yaml
│   ├── backend-deployment.yaml
│   ├── frontend-deployment.yaml
│   ├── scanner-deployment.yaml
│   └── ingress.yaml
│
└── overlays/
    ├── with-infra/                # 完整部署（包含内置数据库）
    │   ├── kustomization.yaml
    │   ├── postgres-statefulset.yaml
    │   └── redis-statefulset.yaml
    │
    └── external/                  # 外部数据库
        └── kustomization.yaml
```

## 快速开始

### 1. 创建命名空间

```bash
kubectl create namespace skillhub
```

### 2. 配置镜像拉取凭证（如镜像私有）

```bash
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<GitHub用户名> \
  --docker-password=<GitHub Token> \
  -n skillhub
```

### 3. 创建应用 Secret

```bash
cd deploy/k8s/base

# 复制示例文件
cp secret.yaml.example secret.yaml

# 编辑 secret.yaml，修改以下值：
# - spring-datasource-url: 数据库连接地址
# - spring-datasource-username: 数据库用户名
# - spring-datasource-password: 数据库密码
# - oauth2-github-client-id: GitHub OAuth 客户端 ID（可选）
# - oauth2-github-client-secret: GitHub OAuth 客户端密钥（可选）
# - skill-scanner-llm-api-key: LLM API 密钥（可选）
```

### 4. 选择部署方式

**方式一：完整部署（包含 PostgreSQL + Redis）**

适合全新环境，自动部署数据库：

```bash
kubectl apply -k overlays/with-infra/
```

**方式二：使用外部数据库**

适合已有 PostgreSQL 和 Redis 的环境：

1. 修改 `base/configmap.yaml` 中的 Redis 配置：
```yaml
redis-host: your-redis-host
redis-port: "6379"
```

2. 修改 `base/secret.yaml` 中的数据库连接：
```yaml
spring-datasource-url: jdbc:postgresql://your-postgres-host:5432/skillhub
```

3. 部署：
```bash
kubectl apply -k overlays/external/
```

### 5. 验证部署

```bash
# 检查 Pod 状态
kubectl get pods -n skillhub

# 等待所有 Pod 就绪
kubectl wait --for=condition=ready pod --all -n skillhub --timeout=300s
```

### 6. 访问服务

**方式一：端口转发（推荐本地测试）**

```bash
# 前端
kubectl port-forward svc/skillhub-web -n skillhub 8080:80

# 后端 API
kubectl port-forward svc/skillhub-server -n skillhub 8081:8080
```

访问 http://localhost:8080

**方式二：Ingress 域名访问**

修改 `base/ingress.yaml` 中的域名：
```yaml
spec:
  rules:
    - host: your-domain.com  # 修改为你的域名
```

```bash
kubectl apply -k overlays/with-infra/  # 或 overlays/external/
```

## 部署架构

```
┌─────────────────────────────────────────────────────────────┐
│                        skillhub namespace                    │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ skillhub-web│  │skillhub-    │  │ skillhub-scanner    │  │
│  │   (前端)    │  │  server     │  │    (扫描器)         │  │
│  │   :80       │  │  (后端)     │  │     :8000           │  │
│  └─────────────┘  │   :8080     │  └─────────────────────┘  │
│                   └──────┬──────┘                            │
│                          │                                   │
│         ┌────────────────┴────────────────┐                  │
│         │         with-infra only          │                 │
│         │  ┌─────────────┐  ┌───────────┐ │                 │
│         │  │  postgres-0 │  │  redis-0  │ │                 │
│         │  │   :5432     │  │   :6379   │ │                 │
│         │  └─────────────┘  └───────────┘ │                 │
│         └─────────────────────────────────┘                 │
│                                                              │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              PersistentVolumeClaims                      │ │
│  │  - skillhub-storage-pvc (10Gi)                          │ │
│  │  - postgres-data-0 (10Gi) - with-infra only             │ │
│  │  - redis-data-0 (5Gi) - with-infra only                 │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

## 配置说明

### ConfigMap (`base/configmap.yaml`)

| 键 | 说明 | 默认值 |
|---|---|---|
| redis-host | Redis 主机地址 | redis |
| redis-port | Redis 端口 | 6379 |
| storage-base-path | 技能存储路径 | /var/lib/skillhub/storage |
| skill-scanner-enabled | 是否启用扫描器 | true |
| skill-scanner-url | 扫描器地址 | http://skillhub-scanner:8000 |
| skill-scanner-mode | 扫描模式 | upload |

### Secret (`base/secret.yaml`)

| 键 | 说明 | 必填 |
|---|---|---|
| spring-datasource-url | PostgreSQL 连接 URL | 是 |
| spring-datasource-username | 数据库用户名 | 是 |
| spring-datasource-password | 数据库密码 | 是 |
| oauth2-github-client-id | GitHub OAuth ID | 否 |
| oauth2-github-client-secret | GitHub OAuth 密钥 | 否 |
| skill-scanner-llm-api-key | LLM API 密钥 | 否 |
| skill-scanner-llm-model | LLM 模型名称 | 否 |

### 持久化存储

| PVC | 大小 | 说明 |
|-----|------|------|
| skillhub-storage-pvc | 10Gi | 技能文件存储 |
| postgres-data-0 | 10Gi | PostgreSQL 数据（with-infra only） |
| redis-data-0 | 5Gi | Redis 数据（with-infra only） |

## 镜像说明

| 组件 | 镜像 |
|---|---|
| 后端服务 | ghcr.io/iflytek/skillhub-server:edge |
| 前端服务 | ghcr.io/iflytek/skillhub-web:edge |
| 扫描器 | ghcr.io/iflytek/skillhub-scanner:edge |
| PostgreSQL | postgres:16-alpine |
| Redis | redis:7-alpine |

## 常见问题

### Pod 一直 Pending

```bash
# 检查 PVC 是否绑定
kubectl get pvc -n skillhub

# 检查节点资源
kubectl describe node <node-name>
```

### 镜像拉取失败

```bash
# 检查凭证是否创建
kubectl get secret ghcr-secret -n skillhub

# 查看详细错误
kubectl describe pod <pod-name> -n skillhub
```

### 数据库连接失败

```bash
# 检查 PostgreSQL 是否就绪
kubectl logs postgres-0 -n skillhub

# 检查 Secret 配置
kubectl get secret skillhub-secret -n skillhub -o yaml
```

### 查看日志

```bash
# 后端日志
kubectl logs -l app.kubernetes.io/name=skillhub-server -n skillhub -f

# 前端日志
kubectl logs -l app.kubernetes.io/name=skillhub-web -n skillhub -f

# 扫描器日志
kubectl logs -l app.kubernetes.io/name=skillhub-scanner -n skillhub -f
```

## 清理

```bash
# 删除所有资源
kubectl delete -k overlays/with-infra/  # 或 overlays/external/

# 删除命名空间
kubectl delete namespace skillhub
```
