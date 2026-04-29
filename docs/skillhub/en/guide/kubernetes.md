# Kubernetes Deployment

This page keeps only the minimum Kubernetes deployment path. Detailed configuration matrices, runtime differences, and tuning strategy should stay in the repository-level deployment and design docs.

## When to Use It

- You already operate a Kubernetes cluster
- You want Ingress, PVCs, and external database integration
- You need a setup closer to production than single-machine Compose

## Prerequisites

- Kubernetes `v1.24+`
- Working `kubectl`
- An available StorageClass
- An image pull, domain, and certificate plan

## Directory Layout

```text
deploy/k8s/
├── base/
└── overlays/
    ├── with-infra/
    └── external/
```

## Minimum Flow

### 1. Create the Namespace

```bash
kubectl create namespace skillhub
```

### 2. Prepare Secrets

At minimum, provide:

- Database connection information
- Admin password
- Optional OAuth, scanner, or object-storage secrets

### 3. Choose a Deployment Mode

```bash
# Built-in PostgreSQL / Redis
kubectl apply -k deploy/k8s/overlays/with-infra/

# External PostgreSQL / Redis
kubectl apply -k deploy/k8s/overlays/external/
```

### 4. Verify the Deployment

```bash
kubectl get pods -n skillhub
kubectl wait --for=condition=ready pod --all -n skillhub --timeout=300s
```

### 5. Verify Access

For local debugging, start with port forwarding:

```bash
kubectl port-forward svc/skillhub-web -n skillhub 8080:80
kubectl port-forward svc/skillhub-server -n skillhub 8081:8080
```

## Operational Notes

- Production deployments should prefer external object storage
- Security scanning usually works best in upload mode
- The bootstrap admin should only be used for initial access
- Back up the database and object storage before upgrades

## Common Issues

- Pending Pods: check PVCs, resources, and scheduling constraints
- Image pull failures: check registry credentials
- Database connection failures: check Secrets, ConfigMaps, and network reachability

## Continue Reading

- [Quick Start](/en/quickstart)
- [Security Scanning](/en/guide/scanner)
