# Kubernetes 部署

本页只保留 Kubernetes 部署的最小路径。更细的配置项、运行时差异和调优策略，建议回到仓库中的部署与设计文档继续查看。

## 适用场景

- 已有 Kubernetes 集群
- 希望用 Ingress、PVC、外部数据库等标准能力部署 SkillHub
- 需要比单机 Compose 更接近生产的运行方式

## 前置条件

- Kubernetes `v1.24+`
- `kubectl` 已可用
- 可用的 StorageClass
- 已准备镜像拉取、域名和证书方案

## 目录结构

```text
deploy/k8s/
├── base/            # 基础配置
└── overlays/
    ├── with-infra/  # 内置 MySQL / Redis
    └── external/    # 外部 MySQL / Redis
```

## 最小部署流程

### 1. 创建命名空间

```bash
kubectl create namespace skillhub
```

### 2. 准备 Secret

至少需要准备：

- 数据库连接信息
- 管理员密码
- 可选的 OAuth / Scanner / 对象存储密钥

### 3. 选择部署模式

```bash
# 自带 MySQL / Redis
kubectl apply -k deploy/k8s/overlays/with-infra/

# 使用外部 MySQL / Redis
kubectl apply -k deploy/k8s/overlays/external/
```

### 4. 验证部署

```bash
kubectl get pods -n skillhub
kubectl wait --for=condition=ready pod --all -n skillhub --timeout=300s
```

### 5. 验证访问

本地调试时可先使用端口转发：

```bash
kubectl port-forward svc/skillhub-web -n skillhub 8080:80
kubectl port-forward svc/skillhub-server -n skillhub 8081:8080
```

## 运维提示

- 生产环境优先使用外部对象存储
- 安全扫描通常建议使用上传模式
- 默认管理员仅用于首登初始化，后续应尽快改密
- 升级前先备份数据库和对象存储

## 常见问题

- Pod Pending：先检查 PVC、节点资源和调度约束
- 镜像拉取失败：检查镜像仓库凭证
- 数据库连接失败：检查 Secret、ConfigMap 和网络连通性

## 继续阅读

- [快速开始](/quickstart)
- [安全扫描](/guide/scanner)
