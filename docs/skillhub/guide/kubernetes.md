# Kubernetes 部署：Kustomize 配置与验收

对象存储可选部署、凭证和迁移说明见[对象存储手册（GitHub）](https://github.com/iflytek/skillhub/blob/main/docs/object-storage.md)。

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

以下相对路径均以仓库的 `deploy/k8s` 为工作目录。先配置 Secret，再渲染检查 `kubectl kustomize overlays/with-infra/`；输出可能包含 Secret，不要公开粘贴。应用清单会创建资源并在后端启动时执行数据库迁移。

### 1. 创建命名空间

```bash
kubectl create namespace skillhub
```

### 2. 配置 Secret

```bash
cd deploy/k8s

# 复制示例文件
cp base/secret.yaml.example base/secret.yaml

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
| skill-scanner-llm-base-url | 本地/自定义 LLM 服务地址 | 否 |
| skill-scanner-llm-model | Scanner 使用的 LLM 模型名 | 否 |

完成填写后单独创建 Secret；当前 Kustomization 不包含 `base/secret.yaml`，只复制文件不会把它部署进集群：

```bash
kubectl apply -n skillhub -f base/secret.yaml
```

还需在自己的部署覆盖层为后端补充公开地址与匿名下载签名密钥，见下文“部署前补齐环境配置”。

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

### PostgreSQL 数据目录兼容性

`with-infra` 会在启动时检查 PostgreSQL PVC 根目录：如果已存在 `PG_VERSION`，继续使用根目录中的旧集群；否则在 `pgdata/` 子目录初始化新集群，避免新 ext4 卷中的 `lost+found` 阻止 `initdb`。升级现有部署不需要移动数据库文件。

回滚到不包含该检测逻辑的旧清单时，如果集群位于 `pgdata/`，必须保留当前启动命令，或显式设置 `PGDATA=/var/lib/postgresql/data/pgdata`。不要把正在运行的数据库目录手动移动到 PVC 根目录。

### 镜像说明

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
kubectl describe deployment skillhub-server -n skillhub
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

仅在确认不再使用该实例并完成数据库、技能存储备份后执行。删除命名空间会删除其中的资源和 PVC；底层数据是否保留取决于存储回收策略。

```bash
# 删除所有资源
kubectl delete -k overlays/with-infra/  # 或 overlays/external/

# 删除命名空间
kubectl delete namespace skillhub
```

## 部署前补齐环境配置

当前基础清单未自动透传所有应用设置。为自己的覆盖层中的 `skillhub-server` Deployment、`server` 容器添加所需 `env`（核对容器名，勿误加到扫描器）：

```yaml
- name: SKILLHUB_PUBLIC_BASE_URL
  value: https://skills.example.com
- name: SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET
  valueFrom:
    secretKeyRef:
      name: skillhub-secret
      key: download-anon-cookie-secret
```

在私有 `base/secret.yaml` 的 `stringData` 中增加 `download-anon-cookie-secret`，填入至少 32 字符的随机值。替换公开地址为实际入口。应用启动会拒绝匿名下载密钥的默认占位值。公开地址、Ingress 域名、TLS 和安全 Cookie 配置应一致；Ingress 不会自动替你签发证书。

S3 模式除了 endpoint、bucket、region，还必须把 Secret 中的 `skillhub-storage-s3-access-key`、`skillhub-storage-s3-secret-key` 分别通过 `secretKeyRef` 映射到 `SKILLHUB_STORAGE_S3_ACCESS_KEY`、`SKILLHUB_STORAGE_S3_SECRET_KEY`。只添加 Secret 键不会注入进程。使用基础清单没有声明的开关（例如 Suite 审核、Bundle 确认）同样需要显式透传，不能套用 Compose 的默认值。

## 升级与验收

1. 固定 `base/kustomization.yaml` 中应用镜像的 `newTag`，保存旧版本与覆盖层；备份数据库、存储和私有 Secret。
2. 先本地渲染所选 overlay，确认镜像、环境引用、Ingress、PVC 与目标 namespace；不要公开渲染后的 Secret。
3. 应用配置后等待 `kubectl rollout status deployment/skillhub-server -n skillhub` 完成，检查 Pod 事件和日志。ConfigMap/Secret 修改并不自动更新已运行进程的环境，必要时在维护窗口滚动重启受影响 Deployment。
4. 从实际入口登录、搜索、发布测试版本、跟踪扫描审核并下载固定版本。Pod Ready 仅说明探针通过。
5. 升级失败先检查迁移/依赖日志；回退镜像前确认数据库兼容性，保留 PVC，不把删除 namespace 当作重试方式。

遇到 `CreateContainerConfigError`，优先检查引用的 Secret 是否创建、必需键是否存在；`CrashLoopBackOff` 查看后端启动日志；`Pending` 检查 PVC 和资源调度。不要输出 Secret 数据作为排障材料。

仓库还提供独立的 [Helm Chart](https://github.com/iflytek/skillhub/tree/main/charts/skillhub)。它的 values、Secret 和依赖配置应按 Chart README 操作，不与本页 Kustomize 路径混用。
