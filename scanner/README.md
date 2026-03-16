# Skill Scanner Service

This directory contains the Docker configuration for the [cisco-ai-defense/skill-scanner](https://github.com/cisco-ai-defense/skill-scanner) service integration.

## Overview

The skill-scanner is a Python-based security analysis tool that provides multiple scanning modes:
- **pattern**: Rule-based pattern matching
- **static**: Static code analysis
- **llm**: LLM-powered semantic analysis (requires API key)

## Quick Start

### Basic Usage (without LLM)

```bash
# Build and start scanner service
docker compose --profile scanner up -d skill-scanner

# Check health
curl http://localhost:8000/health

# List available analyzers
curl http://localhost:8000/analyzers
```

### With LLM Support

1. Copy environment template:
```bash
cp scanner/.env.example scanner/.env
```

2. Configure LLM credentials in `scanner/.env`:
```env
SKILL_SCANNER_LLM_API_KEY=your-api-key-here
SKILL_SCANNER_LLM_MODEL=gpt-4
```

3. Start with environment:
```bash
docker compose --profile scanner --env-file scanner/.env up -d skill-scanner
```

## Staging Environment

### Start staging with scanner

```bash
make staging-scanner
```

This will:
1. Build backend JAR and Docker image
2. Build scanner Docker image
3. Build frontend static files
4. Start all services (postgres, redis, minio, server, web, scanner)
5. Run smoke tests

### Environment Variables

The server automatically connects to scanner when enabled:
- `SKILLHUB_SECURITY_SCANNER_ENABLED=true` - Enable scanner integration
- `SKILLHUB_SECURITY_SCANNER_URL=http://skill-scanner:8000` - Scanner API endpoint
- `SKILLHUB_SECURITY_SCANNER_MODE=local` - Use local file sharing via volume

### Shared Volume

Server and scanner share `/tmp/skillhub-scans` via the `scanner_tmp` volume for efficient local file transfer.

## API Endpoints

- `GET /health` - Health check
- `GET /analyzers` - List available analyzers
- `POST /scan` - Submit scan request

## Troubleshooting

### Check scanner logs
```bash
docker compose --profile scanner logs -f skill-scanner
```

### Verify scanner connectivity from server
```bash
docker compose exec server wget -qO- http://skill-scanner:8000/health
```

### Rebuild scanner image
```bash
docker compose --profile scanner build skill-scanner
```

## Architecture

- **Base Image**: `python:3.11-alpine`
- **Package**: `cisco-ai-skill-scanner` (from PyPI)
- **Port**: 8000
- **User**: Non-root `app` (consistent with project conventions)
- **Health Check**: HTTP GET `/health` every 10s via `wget`
