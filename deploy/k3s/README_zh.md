# K3s 部署指南

本文档说明如何在 K3s 集群中部署 SkillHub。

## 前置条件

- K3s 集群已就绪（单节点或多节点均可）
- `kubectl` 已配置并能访问集群
- K3s 默认 StorageClass 可用（通常为 `local-path`）

## 目录结构

```
deploy/k3s/
├── README_zh.md
└── overlays/
    └── quickstart/                # 一键部署（内置 PostgreSQL + Redis）
        ├── README.md
        ├── kustomization.yaml
        ├── namespace.yaml
        ├── secret.yaml
        ├── configmap.yaml
        ├── services.yaml
        ├── backend-deployment.yaml
        ├── frontend-deployment.yaml
        ├── scanner-deployment.yaml
        ├── ingress.yaml
        ├── postgres-statefulset.yaml
        └── redis-statefulset.yaml
```

`quickstart` 目录已包含完整部署所需的全部资源文件，无需依赖 `deploy/k8s/` 目录。当前 K3s 部署仅提供该覆盖层，适合快速体验或本地 K3s 环境。生产环境建议在此基础上自行调整 Secret、域名和镜像版本。

## 组件说明

| 组件 | 类型 | 镜像 | 说明 |
|------|------|------|------|
| **skillhub-server** | Deployment | `ghcr.io/iflytek/skillhub-server:latest` | Spring Boot 后端服务，端口 `8080`，profile 为 `docker` |
| **skillhub-web** | Deployment | `ghcr.io/iflytek/skillhub-web:latest` | Nginx 前端服务，端口 `80`，API 上游指向 `skillhub-server:8080` |
| **skillhub-scanner** | Deployment | `ghcr.io/iflytek/skillhub-scanner:latest` | Python 安全扫描器，端口 `8000` |
| **postgres** | StatefulSet | `postgres:16-alpine` | 内置 PostgreSQL 数据库，端口 `5432` |
| **redis** | StatefulSet | `redis:7-alpine` | 内置 Redis，AOF 持久化，端口 `6379` |

## 网络与路由

K3s 默认使用 **Traefik** 作为 Ingress Controller。`quickstart` 配置已移除 `ingressClassName: nginx` 限制，Traefik 会自动接管。

- `/api/*` → `skillhub-server:8080`
- `/*` → `skillhub-web:80`
- 默认域名：`skillhub.local`
- 文件上传大小限制：`100m`

## 存储说明

| PVC | 大小 | 用途 |
|-----|------|------|
| `skillhub-storage-pvc` | 10Gi | 技能包本地存储（挂载到后端 `/var/lib/skillhub/storage`） |
| `postgres-data-0` | 10Gi | PostgreSQL 数据持久化 |
| `redis-data-0` | 5Gi | Redis AOF 数据持久化 |

所有 PVC 均不指定 `storageClassName`，直接采用集群默认 StorageClass（K3s 通常为 `local-path`）。

## 配置说明

### ConfigMap

`quickstart/configmap.yaml` 包含以下关键配置：

| 键 | 默认值 | 说明 |
|---|---|---|
| `redis-host` | `redis` | Redis 主机地址 |
| `redis-port` | `6379` | Redis 端口 |
| `storage-base-path` | `/var/lib/skillhub/storage` | 本地存储挂载路径 |
| `skillhub-storage-provider` | `local` | 存储类型：`local` 或 `s3` |
| `skill-scanner-enabled` | `true` | 是否启用扫描器 |
| `skill-scanner-url` | `http://skillhub-scanner:8000` | 扫描器内部地址 |
| `bootstrap-admin-enabled` | `true` | 是否创建默认管理员 |
| `session-cookie-secure` | `false` | HTTPS 环境请改为 `true` |

### Secret

`quickstart/secret.yaml` 已内置默认 Secret，首次部署无需手动创建。

**注意**：生产环境部署前，务必修改 `secret.yaml` 中的默认值！

| 键 | 默认值 | 说明 |
|---|---|---|
| `spring-datasource-url` | `jdbc:postgresql://postgres:5432/skillhub` | 数据库连接 URL |
| `spring-datasource-username` | `skillhub` | 数据库用户名 |
| `spring-datasource-password` | `skillhub` | 数据库密码 |
| `bootstrap-admin-password` | `ChangeMe!2026` | 默认管理员密码 |
| `oauth2-github-client-id` | `""` | GitHub OAuth ID（可选） |
| `oauth2-github-client-secret` | `""` | GitHub OAuth 密钥（可选） |
| `skill-scanner-llm-api-key` | `""` | LLM API Key（可选） |
| `skill-scanner-llm-model` | `""` | LLM 模型名称（可选） |

## 快速开始

### 一键部署

```bash
kubectl apply -k deploy/k3s/overlays/quickstart/
```

该命令会自动完成：
1. 创建 `skillhub` 命名空间
2. 部署 PostgreSQL 和 Redis（StatefulSet + PVC）
3. 部署后端、前端、扫描器服务
4. 创建 Service、Ingress 和默认 Secret

### 验证部署

```bash
# 查看 Pod 状态
kubectl get pods -n skillhub

# 等待所有 Pod 就绪
kubectl wait --for=condition=ready pod --all -n skillhub --timeout=300s
```

### 访问服务

#### 本地测试（修改 /etc/hosts）

1. 获取 K3s 节点 IP：
```bash
kubectl get nodes -o wide
```

2. 配置本地 hosts：
```bash
echo "<NODE_IP> skillhub.local" | sudo tee -a /etc/hosts
```

3. 浏览器访问：
- **Web UI**: http://skillhub.local
- **API**: http://skillhub.local/api

#### 生产环境

修改 `deploy/k3s/overlays/quickstart/kustomization.yaml` 中的 Ingress host patch：

```yaml
- op: replace
  path: /spec/rules/0/host
  value: your-domain.com
```

然后重新部署：
```bash
kubectl apply -k deploy/k3s/overlays/quickstart/
```

## 默认管理员

首次启动时，如果 `bootstrap-admin-enabled` 为 `true`，系统会自动创建管理员账户：

- **用户名**: `admin`
- **密码**: `ChangeMe!2026`

**安全建议**：首次登录后请立即修改默认密码；生产环境请务必在部署前修改 `secret.yaml`。

## 生产环境建议

1. **修改 Secret 默认值**：不要直接使用 `quickstart/secret.yaml` 中的默认密码。
2. **锁定镜像版本**：`quickstart/kustomization.yaml` 中默认使用 `latest` tag，建议改为具体的版本号。
3. **存储切换为 S3**：
   - 在 ConfigMap 中将 `skillhub-storage-provider` 改为 `s3`
   - 在 Secret 中配置 `skillhub-storage-s3-access-key` 和 `skillhub-storage-s3-secret-key`
   - 在 `backend-deployment.yaml` 环境变量中补充 `S3_ENDPOINT`、`S3_BUCKET`、`S3_REGION`
4. **启用 HTTPS**：将 `session-cookie-secure` 设为 `true`，并配置 TLS 证书。
5. **使用外部数据库**：对于高可用场景，建议在外部维护 PostgreSQL 和 Redis 集群，并切换到 `external` 模式部署。

## 常见问题

### Pod 一直 Pending

```bash
# 检查 PVC 是否绑定
kubectl get pvc -n skillhub

# 检查 StorageClass
kubectl get storageclass

# 检查节点资源
kubectl describe node <node-name>
```

### 镜像拉取失败

如果镜像是私有的，需要创建拉取凭证：

```bash
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<GitHub用户名> \
  --docker-password=<GitHub Token> \
  -n skillhub
```

然后在 Deployment 的 `imagePullSecrets` 中引用该 Secret。

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
kubectl delete -k deploy/k3s/overlays/quickstart/

# 删除命名空间
kubectl delete namespace skillhub
```
