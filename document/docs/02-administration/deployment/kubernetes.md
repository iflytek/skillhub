---
title: Kubernetes 部署
sidebar_position: 2
description: 在 Kubernetes 集群中部署 SkillHub
---

# Kubernetes 部署

本文介绍如何在 Kubernetes 集群中部署 SkillHub。

## 前置要求

- Kubernetes 1.24+
- kubectl 配置完成
- Helm 3.0+（可选）
- 可用的持久化存储类

## 部署清单

项目提供了 Kubernetes 部署清单：

```bash
cd deploy/k8s

# 1. 创建命名空间
kubectl create namespace skillhub

# 2. 配置 Secret
cp secret.yaml.example secret.yaml
# 编辑 secret.yaml 填入真实凭证

# 3. 应用配置
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml

# 4. 部署服务
kubectl apply -f backend-deployment.yaml
kubectl apply -f frontend-deployment.yaml
kubectl apply -f services.yaml

# 5. 配置 Ingress
kubectl apply -f ingress.yaml
```

## 高可用配置

- 后端和前端建议至少部署 2 个副本
- PostgreSQL 使用主从复制
- Redis 使用 Sentinel 或 Cluster 模式
- 存储使用高可用对象存储（如 MinIO 集群或云厂商 OSS）

## 下一步

- [配置说明](./configuration) - 详细配置项说明
