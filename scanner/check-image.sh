#!/bin/bash
# 检查镜像是否已经成功推送

IMAGE_NAME="jsessh-registry.cn-shanghai.cr.aliyuncs.com/apps/sh-skill-scanner"

echo "========================================="
echo "检查镜像仓库中的镜像"
echo "========================================="

# 检查 latest 标签
echo "检查 latest 标签..."
docker pull ${IMAGE_NAME}:latest && echo "✅ latest 标签存在" || echo "❌ latest 标签不存在"

echo ""

# 列出所有标签（需要 aliyun CLI）
if command -v aliyun &> /dev/null; then
    echo "列出所有标签..."
    aliyun cr GET /repos/apps/sh-skill-scanner/tags || echo "无法列出标签"
else
    echo "💡 提示: 安装阿里云 CLI 可以列出所有标签"
    echo "   aliyun cr GET /repos/apps/sh-skill-scanner/tags"
fi

echo ""
echo "========================================="
echo "检查完成"
echo "========================================="
