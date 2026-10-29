# Kubernetes Deployment: Kustomize Configuration and Verification

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

All relative paths below use `deploy/k8s` in the repository as the working directory. Configure the Secret, then inspect `kubectl kustomize overlays/with-infra/` locally; rendered output may contain secrets. Applying manifests creates resources and backend startup runs database migrations.

### 1. Create Namespace

```bash
kubectl create namespace skillhub
```

### 2. Configure Secret

```bash
cd deploy/k8s

# Copy example file
cp base/secret.yaml.example base/secret.yaml

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
| skill-scanner-llm-base-url | Local/custom LLM service base URL | No |
| skill-scanner-llm-model | LLM model name used by the scanner | No |

Apply the completed Secret separately. The current Kustomization does not include `base/secret.yaml`; copying the file does not create it in the cluster:

```bash
kubectl apply -n skillhub -f base/secret.yaml
```

Also add the public URL and anonymous-download signing secret to your backend overlay; see “Complete environment wiring before deployment” below.

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

### PostgreSQL data-directory compatibility

The `with-infra` overlay checks the PostgreSQL PVC root at startup. If `PG_VERSION` already exists there, the legacy root-level cluster remains in use. Otherwise, a new cluster is initialized under `pgdata/`, avoiding the `lost+found` directory that can block `initdb` on a fresh ext4 volume. Existing installations do not need to move their database files during upgrade.

When rolling back to an older manifest without this detection, keep the current startup command or set `PGDATA=/var/lib/postgresql/data/pgdata` explicitly if the cluster lives under `pgdata/`. Do not move an active database directory back to the PVC root by hand.

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
kubectl describe deployment skillhub-server -n skillhub
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

Run only after retiring the instance and backing up its database and skill storage. Namespace deletion removes its resources and PVCs; underlying data retention depends on the storage reclaim policy.

```bash
# Delete all resources
kubectl delete -k overlays/with-infra/  # or overlays/external/

# Delete namespace
kubectl delete namespace skillhub
```

## Complete environment wiring before deployment

The base manifests do not automatically pass every application setting. Add the required environment entries to the `server` container in your `skillhub-server` Deployment overlay:

```yaml
- name: SKILLHUB_PUBLIC_BASE_URL
  value: https://skills.example.com
- name: SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET
  valueFrom:
    secretKeyRef:
      name: skillhub-secret
      key: download-anon-cookie-secret
```

Add `download-anon-cookie-secret` under `stringData` in your private `base/secret.yaml`, using a random value of at least 32 characters. Replace the public URL. Startup rejects the default anonymous-download secret placeholder. Keep the public URL, Ingress host, TLS and secure-cookie settings aligned; Ingress does not issue certificates automatically.

For S3 storage, also map the Secret keys `skillhub-storage-s3-access-key` and `skillhub-storage-s3-secret-key` through `secretKeyRef` to `SKILLHUB_STORAGE_S3_ACCESS_KEY` and `SKILLHUB_STORAGE_S3_SECRET_KEY`. Merely adding keys to a Secret does not inject them into the process. Explicitly wire settings absent from the base manifest, including Suite review and Bundle confirmation controls; Compose defaults do not apply here.

## Upgrade and verify

1. Pin application image `newTag` values in `base/kustomization.yaml`. Preserve the previous version and overlay, and back up the database, storage and private Secrets.
2. Render the selected overlay locally. Check images, environment references, Ingress, PVCs and namespace; do not publish rendered Secrets.
3. Apply configuration, wait for `kubectl rollout status deployment/skillhub-server -n skillhub`, then inspect Pod events and logs. ConfigMap/Secret changes do not automatically refresh running process environments; roll affected Deployments during an appropriate maintenance window.
4. From the actual entrypoint, verify login, search, a test publication and its scan/review, and an exact-version download. Ready probes alone do not establish business acceptance.
5. For a failed upgrade, inspect migrations and dependencies before rollback. Check schema compatibility and retain PVCs; deleting a namespace is not a retry procedure.

For `CreateContainerConfigError`, check that referenced Secrets and required keys exist. For `CrashLoopBackOff`, inspect startup logs. For `Pending`, check PVCs and scheduling capacity. Do not expose Secret data during troubleshooting.

A separate [Helm Chart](https://github.com/iflytek/skillhub/tree/main/charts/skillhub) is available. Follow its README for values, Secrets and dependencies rather than mixing Helm instructions with this guide's Kustomize paths.
