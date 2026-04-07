# MinIO 迁移到 Ceph 方案

## 背景

当前项目使用 MinIO 作为 S3 兼容对象存储，现需迁移到已部署的 Ceph RGW。由于测试环境暂无生产数据，无需数据迁移，直接配置切换即可。

## 关键发现

**代码层面无需修改**：
- 项目已有 `ObjectStorageService` 抽象接口
- `S3StorageService` 使用 AWS S3 SDK v2，完全兼容 Ceph RGW
- 通过 `@ConditionalOnProperty(name = "skillhub.storage.provider")` 条件装配

## 改动点

### 1. K8s ConfigMap 配置

**文件**: `deploy/k8s/base/configmap.yaml`

```yaml
# 修改存储提供商
skillhub-storage-provider: s3  # 原值: local

# 添加 Ceph S3 配置
skillhub-storage-s3-endpoint: http://ceph-rgw:7480
skillhub-storage-s3-public-endpoint: https://ceph.example.com:7480
skillhub-storage-s3-bucket: skillhub
skillhub-storage-s3-region: default
skillhub-storage-s3-force-path-style: "true"
skillhub-storage-s3-auto-create-bucket: "true"
skillhub-storage-s3-presign-expiry: PT10M
```

**移除本地存储配置**（可选）：
```yaml
# 注释或删除
# storage-base-path: /var/lib/skillhub/storage
```

### 2. K8s Secret 配置

**文件**: `deploy/k8s/base/secret.yaml.example`

```yaml
# 添加 Ceph 凭证
skillhub-storage-s3-access-key: your-ceph-access-key
skillhub-storage-s3-secret-key: your-ceph-secret-key
```

创建实际 Secret：
```bash
kubectl create secret generic skillhub-secret \
  --from-literal=skillhub-storage-s3-access-key=your-access-key \
  --from-literal=skillhub-storage-s3-secret-key=your-secret-key \
  --dry-run=client -o yaml | kubectl apply -f -
```

### 3. Backend Deployment 配置

**文件**: `deploy/k8s/base/backend-deployment.yaml`

**添加 S3 环境变量**（在 env 部分添加）：
```yaml
# Storage - S3
- name: SKILLHUB_STORAGE_S3_ENDPOINT
  valueFrom:
    configMapKeyRef:
      name: skillhub-config
      key: skillhub-storage-s3-endpoint
- name: SKILLHUB_STORAGE_S3_PUBLIC_ENDPOINT
  valueFrom:
    configMapKeyRef:
      name: skillhub-config
      key: skillhub-storage-s3-public-endpoint
- name: SKILLHUB_STORAGE_S3_BUCKET
  valueFrom:
    configMapKeyRef:
      name: skillhub-config
      key: skillhub-storage-s3-bucket
- name: SKILLHUB_STORAGE_S3_ACCESS_KEY
  valueFrom:
    secretKeyRef:
      name: skillhub-secret
      key: skillhub-storage-s3-access-key
- name: SKILLHUB_STORAGE_S3_SECRET_KEY
  valueFrom:
    secretKeyRef:
      name: skillhub-secret
      key: skillhub-storage-s3-secret-key
- name: SKILLHUB_STORAGE_S3_REGION
  valueFrom:
    configMapKeyRef:
      name: skillhub-config
      key: skillhub-storage-s3-region
- name: SKILLHUB_STORAGE_S3_FORCE_PATH_STYLE
  valueFrom:
    configMapKeyRef:
      name: skillhub-config
      key: skillhub-storage-s3-force-path-style
- name: SKILLHUB_STORAGE_S3_AUTO_CREATE_BUCKET
  valueFrom:
    configMapKeyRef:
      name: skillhub-config
      key: skillhub-storage-s3-auto-create-bucket
- name: SKILLHUB_STORAGE_S3_PRESIGN_EXPIRY
  valueFrom:
    configMapKeyRef:
      name: skillhub-config
      key: skillhub-storage-s3-presign-expiry
```

**移除本地存储挂载**（可选）：
```yaml
# 注释或删除 volumeMounts 中的 skillhub-storage
# 注释或删除 volumes 中的 skillhub-storage-pvc
```

### 4. Docker Compose 配置

**文件**: `docker-compose.yml`

**移除 MinIO 服务**：
```yaml
# minio:
#   image: minio/minio:RELEASE.2025-09-07T16-13-09Z
#   ports:
#     - "9000:9000"
#     - "9001:9001"
#   ...
```

### 5. ConfigMap 中移除 PVC 定义

**文件**: `deploy/k8s/base/configmap.yaml`

```yaml
# 注释或删除 PersistentVolumeClaim 部分
# ---
# apiVersion: v1
# kind: PersistentVolumeClaim
# metadata:
#   name: skillhub-storage-pvc
# ...
```

## 执行步骤

1. **更新 ConfigMap**
   ```bash
   kubectl apply -f deploy/k8s/base/configmap.yaml
   ```

2. **创建/更新 Secret**
   ```bash
   kubectl create secret generic skillhub-secret \
     --from-literal=skillhub-storage-s3-access-key=<your-key> \
     --from-literal=skillhub-storage-s3-secret-key=<your-secret> \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

3. **更新 Deployment**
   ```bash
   kubectl apply -f deploy/k8s/base/backend-deployment.yaml
   ```

4. **重启 Pod**
   ```bash
   kubectl rollout restart deployment/skillhub-server
   ```

5. **验证配置**
   ```bash
   kubectl logs -f deployment/skillhub-server
   ```

## 验证测试

### 功能验证清单
- [ ] Pod 正常启动，无错误日志
- [ ] 上传技能包
- [ ] 下载技能包
- [ ] 生成预签名 URL
- [ ] 删除技能包

### 验证命令
```bash
# 检查 Pod 日志
kubectl logs deployment/skillhub-server

# 检查环境变量
kubectl exec deployment/skillhub-server -- env | grep STORAGE

# 测试上传（通过 API）
curl -X POST http://<service>/api/skills/{namespace}/publish \
  -F "file=@test.zip" \
  -F "visibility=public"
```

## 关键文件清单

| 文件路径 | 操作 |
|---------|------|
| `deploy/k8s/base/configmap.yaml` | 修改：添加 S3 配置 |
| `deploy/k8s/base/secret.yaml.example` | 修改：添加 S3 凭证模板 |
| `deploy/k8s/base/backend-deployment.yaml` | 修改：添加 S3 环境变量 |
| `docker-compose.yml` | 修改：移除 MinIO 服务 |

## 注意事项

1. **Ceph 网络连通性**：确保集群能访问 Ceph RGW 的 endpoint
2. **Bucket 创建**：建议提前创建 bucket，或启用 `auto-create-bucket`
3. **Region 配置**：Ceph 通常使用 `default` region
4. **Path Style**：Ceph RGW 需要启用 `force-path-style`
5. **SSL/TLS**：生产环境建议使用 HTTPS

## 回滚方案

如果出现问题，快速回滚：

```bash
# 1. 恢复 ConfigMap
git checkout deploy/k8s/base/configmap.yaml
kubectl apply -f deploy/k8s/base/configmap.yaml

# 2. 恢复 Deployment
git checkout deploy/k8s/base/backend-deployment.yaml
kubectl apply -f deploy/k8s/base/backend-deployment.yaml

# 3. 重启服务
kubectl rollout restart deployment/skillhub-server
```
