#!/bin/bash
# 云效构建脚本 - 简化版，完全禁用 buildx 缓存

set -e

# 镜像配置
IMAGE_NAME="jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/sh-skill-scanner"
IMAGE_TAG="${BUILD_NUMBER:-latest}"
FULL_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"

echo "========================================="
echo "开始构建 Docker 镜像（禁用缓存）"
echo "========================================="
echo "镜像名称: ${FULL_IMAGE}"
echo "构建时间: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""

# 构建镜像（使用 docker build，不使用 buildx）
echo "正在构建镜像..."
docker build \
  --no-cache \
  --pull \
  -t "${FULL_IMAGE}" \
  -f Dockerfile \
  .

echo ""
echo "========================================="
echo "镜像构建成功"
echo "========================================="
echo ""

# 推送镜像
echo "正在推送镜像..."
docker push "${FULL_IMAGE}"

# 打 latest 标签
echo "正在推送 latest 标签..."
docker tag "${FULL_IMAGE}" "${IMAGE_NAME}:latest"
docker push "${IMAGE_NAME}:latest"

echo ""
echo "========================================="
echo "镜像推送成功"
echo "========================================="
echo "镜像地址: ${FULL_IMAGE}"
echo "Latest:   ${IMAGE_NAME}:latest"
echo "========================================="
