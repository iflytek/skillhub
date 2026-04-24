# K3s 一键部署（Quick Start）

基于 Kustomize 的 K3s 快速部署配置，内置 PostgreSQL + Redis，一条命令即可完成部署。

## 前置条件

- 可用的 K3s 集群（单节点或多节点均可）
- `kubectl` 已配置并能访问集群
- K3s 集群默认 StorageClass 可用（通常为 `local-path`）

## 部署

```bash
kubectl apply -k deploy/k3s/overlays/quickstart/
```

该命令会自动完成：
1. 创建 `skillhub` 命名空间
2. 部署 PostgreSQL 和 Redis（StatefulSet + PVC）
3. 部署后端、前端、扫描器服务
4. 创建 Service、Ingress 和默认 Secret

## 验证

```bash
# 查看 Pod 状态
kubectl get pods -n skillhub

# 等待所有 Pod 就绪
kubectl wait --for=condition=ready pod --all -n skillhub --timeout=300s
```

## 访问

K3s 默认使用 **Traefik** 作为 Ingress Controller，此配置已移除 `nginx` 的 `ingressClassName` 限制。

### 本地测试（NodePort / HostPort 场景）

1. 获取 Ingress 暴露的 IP：
```bash
kubectl get ingress skillhub -n skillhub
```

2. 配置本地 hosts（将 `<NODE_IP>` 替换为实际节点 IP）：
```bash
echo "<NODE_IP> skillhub.local" | sudo tee -a /etc/hosts
```

3. 浏览器访问：
- **Web UI**: http://skillhub.local
- **API**: http://skillhub.local/api

### 生产环境

修改 `kustomization.yaml` 中的 Ingress host patch，将 `skillhub.local` 替换为你的真实域名：

```yaml
- op: replace
  path: /spec/rules/0/host
  value: your-domain.com
```

然后重新执行：
```bash
kubectl apply -k deploy/k3s/overlays/quickstart/
```

## 默认管理员

首次启动会自动创建管理员账户：

- **用户名**: `admin`
- **密码**: `ChangeMe!2026`（配置在 `secret.yaml` 中）

**安全建议**：首次登录后请立即修改默认密码；生产环境部署前务必修改 `secret.yaml` 中的默认值。

## 存储说明

| PVC | 大小 | 说明 |
|-----|------|------|
| `skillhub-storage-pvc` | 10Gi | 技能文件本地存储 |
| `postgres-data-0` | 10Gi | PostgreSQL 数据 |
| `redis-data-0` | 5Gi | Redis AOF 持久化 |

## 清理

```bash
kubectl delete -k deploy/k3s/overlays/quickstart/
```

如需同时删除命名空间：
```bash
kubectl delete namespace skillhub
```
