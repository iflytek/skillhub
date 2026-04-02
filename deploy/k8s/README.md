# Kubernetes 部署指南

本文档说明如何在 Kubernetes 集群中部署 SkillHub。

## 前置条件

- Kubernetes 集群 (v1.24+)
- kubectl 已配置并连接到集群
- nginx ingress controller 已安装（可选，用于域名访问）
- 默认 StorageClass 已配置（用于 PVC）

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

### 2. 配置 Secret

```bash
cd deploy/k8s/base

# 复制示例文件
cp secret.yaml.example secret.yaml

# 编辑 secret.yaml，修改敏感配置
```

**Secret 配置项**：

| 键 | 说明 | 必填 |
|---|---|---|
| spring-datasource-url | PostgreSQL 连接 URL | 是 |
| spring-datasource-username | 数据库用户名 | 是 |
| spring-datasource-password | 数据库密码 | 是 |
| bootstrap-admin-password | 管理员密码 | 是 |
| oauth2-github-client-id | GitHub OAuth ID | 否 |
| oauth2-github-client-secret | GitHub OAuth 密钥 | 否 |
| skill-scanner-llm-api-key | LLM API 密钥 | 否 |

### 3. 选择部署方式

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

### 4. 验证部署

```bash
# 检查 Pod 状态
kubectl get pods -n skillhub

# 等待所有 Pod 就绪
kubectl wait --for=condition=ready pod --all -n skillhub --timeout=300s
```

### 5. 访问服务

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

### ConfigMap 配置项

| 键 | 默认值 | 说明 |
|---|---|---|
| redis-host | redis | Redis 主机地址 |
| redis-port | 6379 | Redis 端口 |
| storage-base-path | /var/lib/skillhub/storage | 技能存储路径 |
| skillhub-storage-provider | local | 存储类型（local/s3） |
| skill-scanner-enabled | true | 是否启用扫描器 |
| skill-scanner-url | http://skillhub-scanner:8000 | 扫描器地址 |
| skill-scanner-mode | upload | 扫描模式 |
| bootstrap-admin-enabled | true | 是否创建默认管理员 |
| bootstrap-admin-user-id | docker-admin | 管理员用户 ID |
| bootstrap-admin-username | admin | 管理员用户名 |
| bootstrap-admin-display-name | Platform Admin | 管理员显示名称 |
| bootstrap-admin-email | admin@example.com | 管理员邮箱 |
| session-cookie-secure | false | HTTPS 环境设为 true |

### Secret 配置项

| 键 | 说明 | 必填 |
|---|---|---|
| spring-datasource-url | PostgreSQL 连接 URL | 是 |
| spring-datasource-username | 数据库用户名 | 是 |
| spring-datasource-password | 数据库密码 | 是 |
| bootstrap-admin-password | 管理员密码 | 是 |
| oauth2-github-client-id | GitHub OAuth ID | 否 |
| oauth2-github-client-secret | GitHub OAuth 密钥 | 否 |
| skill-scanner-llm-api-key | LLM API 密钥 | 否 |
| skill-scanner-llm-model | LLM 模型名称 | 否 |

### 存储配置

**本地存储（默认）**

默认使用本地文件存储，数据保存在 PVC `skillhub-storage-pvc` 中。

**S3/OSS 存储**

生产环境建议使用 S3 兼容的对象存储：

1. 修改 ConfigMap：
```yaml
skillhub-storage-provider: s3
```

2. 在 Secret 中添加：
```yaml
skillhub-storage-s3-access-key: your-access-key
skillhub-storage-s3-secret-key: your-secret-key
```

3. 在 backend-deployment.yaml 中添加环境变量：
```yaml
- name: SKILLHUB_STORAGE_S3_ENDPOINT
  value: https://oss-cn-shanghai.aliyuncs.com
- name: SKILLHUB_STORAGE_S3_BUCKET
  value: skillhub-prod
- name: SKILLHUB_STORAGE_S3_REGION
  value: cn-shanghai
```

### 持久化存储

| PVC | 大小 | 说明 |
|-----|------|------|
| skillhub-storage-pvc | 10Gi | 技能文件存储 |
| postgres-data-0 | 10Gi | PostgreSQL 数据（with-infra only） |
| redis-data-0 | 5Gi | Redis 数据（with-infra only） |

## 镜像说明

| 组件 | 镜像 |
|---|---|
| 后端服务 | ghcr.io/iflytek/skillhub-server:latest |
| 前端服务 | ghcr.io/iflytek/skillhub-web:latest |
| 扫描器 | ghcr.io/iflytek/skillhub-scanner:latest |
| PostgreSQL | postgres:16-alpine |
| Redis | redis:7-alpine |

## 默认管理员

首次启动时，如果 `bootstrap-admin-enabled` 为 `true`，系统会自动创建管理员账户：

- 用户名：`admin`
- 密码：在 `secret.yaml` 的 `bootstrap-admin-password` 中配置

**安全建议**：首次登录后，请立即修改默认密码。

## 常见问题

### Pod 一直 Pending

```bash
# 检查 PVC 是否绑定
kubectl get pvc -n skillhub

# 检查节点资源
kubectl describe node <node-name>
```

### 镜像拉取失败

如果镜像私有，需要创建拉取凭证：

```bash
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<GitHub用户名> \
  --docker-password=<GitHub Token> \
  -n skillhub
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
