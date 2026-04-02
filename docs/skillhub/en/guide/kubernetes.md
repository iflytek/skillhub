# Kubernetes Deployment Guide

This document explains how to deploy SkillHub on a Kubernetes cluster.

## Prerequisites

- Kubernetes cluster (v1.24+)
- kubectl configured and connected to the cluster
- nginx ingress controller installed (optional, for domain access)
- Default StorageClass configured (for PVC)

## Directory Structure

```
deploy/k8s/
├── base/                          # Base configuration (shared by all scenarios)
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
    ├── with-infra/                # Full deployment (with built-in database)
    │   ├── kustomization.yaml
    │   ├── postgres-statefulset.yaml
    │   └── redis-statefulset.yaml
    │
    └── external/                  # External database
        └── kustomization.yaml
```

## Quick Start

### 1. Create Namespace

```bash
kubectl create namespace skillhub
```

### 2. Configure Secret

```bash
cd deploy/k8s/base

# Copy example file
cp secret.yaml.example secret.yaml

# Edit secret.yaml with your configuration
```

**Secret Configuration Items**:

| Key | Description | Required |
|---|---|---|
| spring-datasource-url | PostgreSQL connection URL | Yes |
| spring-datasource-username | Database username | Yes |
| spring-datasource-password | Database password | Yes |
| bootstrap-admin-password | Admin password | Yes |
| oauth2-github-client-id | GitHub OAuth ID | No |
| oauth2-github-client-secret | GitHub OAuth secret | No |
| skill-scanner-llm-api-key | LLM API key | No |

### 3. Choose Deployment Method

**Option 1: Full Deployment (with PostgreSQL + Redis)**

Suitable for new environments, automatically deploys databases:

```bash
kubectl apply -k overlays/with-infra/
```

**Option 2: Use External Database**

Suitable for environments with existing PostgreSQL and Redis:

1. Modify Redis configuration in `base/configmap.yaml`:
```yaml
redis-host: your-redis-host
redis-port: "6379"
```

2. Modify database connection in `base/secret.yaml`:
```yaml
spring-datasource-url: jdbc:postgresql://your-postgres-host:5432/skillhub
```

3. Deploy:
```bash
kubectl apply -k overlays/external/
```

### 4. Verify Deployment

```bash
# Check Pod status
kubectl get pods -n skillhub

# Wait for all Pods to be ready
kubectl wait --for=condition=ready pod --all -n skillhub --timeout=300s
```

### 5. Access Services

**Option 1: Port Forwarding (recommended for local testing)**

```bash
# Frontend
kubectl port-forward svc/skillhub-web -n skillhub 8080:80

# Backend API
kubectl port-forward svc/skillhub-server -n skillhub 8081:8080
```

Visit http://localhost:8080

**Option 2: Ingress Domain Access**

Modify the domain in `base/ingress.yaml`:
```yaml
spec:
  rules:
    - host: your-domain.com  # Change to your domain
```

```bash
kubectl apply -k overlays/with-infra/  # or overlays/external/
```

## Deployment Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        skillhub namespace                    │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ skillhub-web│  │skillhub-    │  │ skillhub-scanner    │  │
│  │  (frontend) │  │  server     │  │    (scanner)        │  │
│  │   :80       │  │  (backend)  │  │     :8000           │  │
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

## Configuration Reference

### ConfigMap Items

| Key | Default | Description |
|---|---|---|
| redis-host | redis | Redis host address |
| redis-port | 6379 | Redis port |
| storage-base-path | /var/lib/skillhub/storage | Skill storage path |
| skillhub-storage-provider | local | Storage type (local/s3) |
| skill-scanner-enabled | true | Enable scanner |
| skill-scanner-url | http://skillhub-scanner:8000 | Scanner URL |
| skill-scanner-mode | upload | Scan mode |
| bootstrap-admin-enabled | true | Create default admin |
| bootstrap-admin-user-id | docker-admin | Admin user ID |
| bootstrap-admin-username | admin | Admin username |
| bootstrap-admin-display-name | Platform Admin | Admin display name |
| bootstrap-admin-email | admin@example.com | Admin email |
| session-cookie-secure | false | Set to true for HTTPS |

### Storage Configuration

**Local Storage (Default)**

Uses local file storage by default, data is stored in PVC `skillhub-storage-pvc`.

**S3/OSS Storage**

For production, S3-compatible object storage is recommended:

1. Modify ConfigMap:
```yaml
skillhub-storage-provider: s3
```

2. Add to Secret:
```yaml
skillhub-storage-s3-access-key: your-access-key
skillhub-storage-s3-secret-key: your-secret-key
```

3. Add environment variables to backend-deployment.yaml:
```yaml
- name: SKILLHUB_STORAGE_S3_ENDPOINT
  value: https://s3.amazonaws.com
- name: SKILLHUB_STORAGE_S3_BUCKET
  value: skillhub-prod
- name: SKILLHUB_STORAGE_S3_REGION
  value: us-east-1
```

### Image Reference

| Component | Image |
|---|---|
| Backend | ghcr.io/iflytek/skillhub-server:latest |
| Frontend | ghcr.io/iflytek/skillhub-web:latest |
| Scanner | ghcr.io/iflytek/skillhub-scanner:latest |
| PostgreSQL | postgres:16-alpine |
| Redis | redis:7-alpine |

## Default Admin

On first startup, if `bootstrap-admin-enabled` is `true`, the system will automatically create an admin account:

- Username: `admin`
- Password: Configured in `secret.yaml` as `bootstrap-admin-password`

**Security Note**: Please change the default password immediately after first login.

## Troubleshooting

### Pod Stuck in Pending

```bash
# Check PVC binding
kubectl get pvc -n skillhub

# Check node resources
kubectl describe node <node-name>
```

### Image Pull Failure

If images are private, create pull credentials:

```bash
kubectl create secret docker-registry ghcr-secret \
  --docker-server=ghcr.io \
  --docker-username=<GitHub-username> \
  --docker-password=<GitHub-Token> \
  -n skillhub
```

### Database Connection Failure

```bash
# Check PostgreSQL status
kubectl logs postgres-0 -n skillhub

# Check Secret configuration
kubectl get secret skillhub-secret -n skillhub -o yaml
```

### View Logs

```bash
# Backend logs
kubectl logs -l app.kubernetes.io/name=skillhub-server -n skillhub -f

# Frontend logs
kubectl logs -l app.kubernetes.io/name=skillhub-web -n skillhub -f

# Scanner logs
kubectl logs -l app.kubernetes.io/name=skillhub-scanner -n skillhub -f
```

## Cleanup

```bash
# Delete all resources
kubectl delete -k overlays/with-infra/  # or overlays/external/

# Delete namespace
kubectl delete namespace skillhub
```
