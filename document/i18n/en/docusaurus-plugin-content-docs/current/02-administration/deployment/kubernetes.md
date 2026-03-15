---
title: Kubernetes Deployment
sidebar_position: 2
description: Deploy SkillHub in a Kubernetes cluster
---

# Kubernetes Deployment

This guide describes how to deploy SkillHub in a Kubernetes cluster.

## Prerequisites

- Kubernetes 1.24+
- kubectl configured
- Helm 3.0+ (optional)
- Available persistent storage class

## Deployment Manifests

Kubernetes deployment manifests are provided in the project:

```bash
cd deploy/k8s

# 1. Create namespace
kubectl create namespace skillhub

# 2. Configure Secret
cp secret.yaml.example secret.yaml
# Edit secret.yaml and fill in real credentials

# 3. Apply configuration
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml

# 4. Deploy services
kubectl apply -f backend-deployment.yaml
kubectl apply -f frontend-deployment.yaml
kubectl apply -f services.yaml

# 5. Configure Ingress
kubectl apply -f ingress.yaml
```

## High Availability Configuration

- Deploy at least 2 replicas for backend and frontend
- Use PostgreSQL with primary-replica replication
- Use Redis with Sentinel or Cluster mode
- Use highly available object storage (like MinIO cluster or cloud provider OSS)

## Next Steps

- [Configuration](./configuration) - Detailed configuration reference
