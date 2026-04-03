# 云效完全禁用 Docker 缓存指南

## 🔍 问题分析

错误信息：
```
ERROR: error writing manifest blob: failed commit on ref "sha256:...": 
unexpected status from PUT request to .../flow-docker-build-cache: 409 Conflict
```

**原因：** 云效使用了 `docker buildx build` 并自动配置了 `--cache-to` 参数，导致缓存写入冲突。

**重要：** 从日志看，镜像本身已经构建成功，只是缓存导出失败。

---

## ✅ 解决方案（按优先级排序）

### 方案一：使用 script 步骤替代 docker_build 步骤（推荐）

在云效 Flow 中，**不使用** `docker_build@1` 组件，改用 `script@1`：

#### 1. 进入云效 Flow 流水线配置

#### 2. 删除或禁用现有的 `docker_build@1` 步骤

#### 3. 添加自定义脚本步骤

```yaml
steps:
  - step: script@1
    name: 构建并推送镜像
    with:
      script: |
        #!/bin/bash
        set -e
        
        IMAGE_NAME="jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/sh-skill-scanner"
        IMAGE_TAG="${BUILD_NUMBER}"
        
        # 使用 docker build（不是 buildx）
        docker build \
          --no-cache \
          --pull \
          -t "${IMAGE_NAME}:${IMAGE_TAG}" \
          -f scanner/Dockerfile \
          scanner/
        
        # 推送镜像
        docker push "${IMAGE_NAME}:${IMAGE_TAG}"
        docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "${IMAGE_NAME}:latest"
        docker push "${IMAGE_NAME}:latest"
```

---

### 方案二：在 docker_build 步骤中禁用缓存

如果必须使用 `docker_build@1` 组件，修改其配置：

```yaml
steps:
  - step: docker_build@1
    with:
      dockerfile_path: scanner/Dockerfile
      image_name: jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/sh-skill-scanner
      tag: ${BUILD_NUMBER}
      # 关键：禁用缓存相关配置
      use_cache: false
      cache_from: ""
      cache_to: ""
      build_args: "--no-cache --pull"
```

---

### 方案三：使用 Docker Buildx 但禁用远程缓存

如果云效强制使用 buildx：

```yaml
steps:
  - step: script@1
    with:
      script: |
        #!/bin/bash
        set -e
        
        IMAGE_NAME="jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/sh-skill-scanner"
        IMAGE_TAG="${BUILD_NUMBER}"
        
        # 使用 buildx 但不使用远程缓存
        docker buildx build \
          --no-cache \
          --pull \
          --load \
          --cache-from=type=inline \
          -t "${IMAGE_NAME}:${IMAGE_TAG}" \
          -f scanner/Dockerfile \
          scanner/
        
        docker push "${IMAGE_NAME}:${IMAGE_TAG}"
        docker tag "${IMAGE_NAME}:${IMAGE_TAG}" "${IMAGE_NAME}:latest"
        docker push "${IMAGE_NAME}:latest"
```

关键参数：
- `--no-cache`: 不使用构建缓存
- `--pull`: 总是拉取最新基础镜像
- `--load`: 将镜像加载到本地 Docker（不是导出到 registry）
- `--cache-from=type=inline`: 只使用内联缓存（不使用远程）
- **不要有 `--cache-to` 参数**

---

### 方案四：删除冲突的缓存标签（临时方案）

如果以上方案都无法实施，手动删除冲突的缓存：

```yaml
steps:
  - step: script@1
    name: 清理冲突缓存
    with:
      script: |
        #!/bin/bash
        # 尝试删除冲突的缓存标签
        docker rmi jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/sh-skill-scanner:flow-docker-build-cache || true
        
        # 或使用阿里云 CLI 删除
        # aliyun cr DELETE /repos/apps/sh-skill-scanner/tags/flow-docker-build-cache || true
  
  - step: docker_build@1
    # ... 原有的构建配置
```

---

## 🔍 验证镜像是否已经成功

虽然缓存导出失败，但镜像本身可能已经成功：

### 1. 在云效中检查

查看构建日志，找到这些关键信息：
```
#14 exporting to image
#14 exporting layers
#14 exporting manifest
#14 pushing layers
```

如果看到 `pushing layers` 完成，说明镜像已经推送成功。

### 2. 手动验证

```bash
# 运行检查脚本
cd scanner && ./check-image.sh

# 或手动拉取测试
docker pull jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/sh-skill-scanner:latest
```

### 3. 在阿里云容器镜像服务控制台查看

登录阿里云控制台 → 容器镜像服务 → 镜像仓库 → `apps/sh-skill-scanner` → 查看标签列表

---

## 📊 各方案对比

| 方案 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| 方案一：script 步骤 | 完全控制，最可靠 | 需要手动写脚本 | ⭐⭐⭐⭐⭐ |
| 方案二：禁用缓存配置 | 简单，UI 配置 | 可能被云效覆盖 | ⭐⭐⭐⭐ |
| 方案三：buildx 不用远程缓存 | 兼容 buildx | 配置复杂 | ⭐⭐⭐ |
| 方案四：删除缓存标签 | 快速临时修复 | 治标不治本 | ⭐⭐ |

---

## 🎯 推荐操作流程

### 第一步：检查镜像是否已经成功

```bash
# 在本地或云效中运行
docker pull jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/sh-skill-scanner:latest
```

**如果拉取成功：**
- ✅ 镜像已经构建并推送成功
- ✅ 可以忽略缓存错误
- ✅ 直接部署即可

**如果拉取失败：**
- ❌ 需要修复构建配置
- 继续下面的步骤

### 第二步：修改云效配置

使用**方案一**（推荐）或**方案二**修改流水线配置。

### 第三步：重新运行构建

保存配置后，重新触发构建。

### 第四步：验证部署

```bash
# 查看 K8s 中的 Pod
kubectl get pods -l app=skillhub-scanner

# 查看 Pod 日志
kubectl logs -l app=skillhub-scanner --tail=50

# 测试健康检查
kubectl exec -it <pod-name> -- wget -qO- http://localhost:8000/health
```

---

## 💡 为什么会有缓存冲突？

可能的原因：

1. **并发构建**
   - 多个构建任务同时写入同一个缓存标签
   - 云效的 `flow-docker-build-cache` 是固定标签名

2. **缓存标签被锁定**
   - 上一次构建异常退出，缓存标签处于锁定状态
   - 镜像仓库的缓存管理机制问题

3. **权限问题**
   - 构建账号对缓存标签没有覆盖权限
   - 镜像仓库配置了只读策略

---

## 📞 还是无法解决？

如果以上方案都无法解决，可能需要：

1. **联系云效技术支持**
   - 确认云效的 Docker 构建机制
   - 是否可以禁用默认的缓存行为

2. **联系阿里云容器镜像服务**
   - 检查镜像仓库配置
   - 确认缓存标签的权限设置

3. **使用其他构建方式**
   - 在云效外部构建（如本地或 GitHub Actions）
   - 只用云效做部署

---

## 📝 使用我提供的 .flow.yml

我已经创建了一个完整的流水线配置文件：`scanner/.flow.yml`

**使用步骤：**

1. 提交文件到 Git
   ```bash
   git add scanner/.flow.yml
   git commit -m "feat: 添加禁用缓存的流水线配置"
   git push
   ```

2. 在云效中配置
   - 进入 Flow 流水线
   - 点击"编辑" → "流水线即代码"
   - 选择"使用代码库中的配置文件"
   - 文件路径：`scanner/.flow.yml`
   - 保存

3. 运行构建
   - 点击"运行"
   - 观察构建日志

这个配置使用 `docker build`（不是 buildx），完全避免了缓存冲突问题。
