# RISC-V (`linux/riscv64`) support

## Current scope

RISC-V support is incremental. The SkillHub server and web images have
`linux/riscv64` build and runtime paths. The security scanner and the complete
Docker Compose deployment are not yet supported on RISC-V.

| Component | `linux/riscv64` status | Notes |
| --- | --- | --- |
| `skillhub-server` | Supported | The architecture-neutral Java 21 JAR is built on the Buildx host and copied into the target-architecture Eclipse Temurin runtime. |
| `skillhub-web` | Supported | Static assets are built on the Buildx host and served by a target-architecture Nginx runtime. |
| `skillhub-scanner` | Not yet verified | Its Python dependency tree still needs a native-extension and runtime audit. |
| PostgreSQL 16 and Redis 7 | Upstream images available | Keep these images explicitly pinned and verify them on the target board before production use. |
| Complete Compose stack | Unsupported | `compose.release.yml` starts the unverified scanner, so do not deploy it unchanged on RISC-V. |

## Build the supported images

Buildx can create both images from an AMD64 or ARM64 host. Register a RISC-V
QEMU handler before running these commands when the host is not RISC-V:

```bash
docker run --privileged --rm tonistiigi/binfmt --install riscv64
docker buildx create --use --name skillhub-riscv64

docker buildx build \
  --platform linux/riscv64 \
  --file server/Dockerfile \
  --tag skillhub-server:riscv64 \
  --load \
  server

docker buildx build \
  --platform linux/riscv64 \
  --file web/Dockerfile \
  --tag skillhub-web:riscv64 \
  --load \
  web
```

The release workflow publishes `linux/amd64`, `linux/arm64`, and
`linux/riscv64` variants for `skillhub-server` and `skillhub-web`. The scanner
remains limited to its existing AMD64/ARM64 platform list.

## Verification boundary

The pull-request workflow builds both supported target images, checks their OCI
architecture metadata, and executes the Java and Nginx runtimes under RISC-V
emulation. This is a component-image guardrail, not a full-stack integration
test. A native RISC-V smoke test with PostgreSQL, Redis, object storage, and a
verified scanner remains required before claiming complete deployment support.
