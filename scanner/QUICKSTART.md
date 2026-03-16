# Scanner Quick Reference

## Start Scanner

```bash
# Basic (no LLM)
docker compose --profile scanner up -d skill-scanner

# With LLM (configure .env first)
cp scanner/.env.example scanner/.env
# Edit scanner/.env with API key
docker compose --profile scanner --env-file scanner/.env up -d skill-scanner

# Staging with scanner
make staging-scanner
```

## Verify Scanner

```bash
# Health check
curl http://localhost:8000/health

# List analyzers
curl http://localhost:8000/analyzers

# Run verification script
./scripts/verify-scanner.sh
```

## Stop Scanner

```bash
# Stop scanner only
docker compose --profile scanner down

# Stop staging (including scanner)
make staging-down
```

## Logs

```bash
# Scanner logs
docker compose --profile scanner logs -f skill-scanner

# Staging scanner logs
docker compose -p skillhub-staging logs -f skill-scanner
```

## Configuration

### Scanner Environment Variables
- `SKILL_SCANNER_LLM_API_KEY` - LLM API key (optional)
- `SKILL_SCANNER_LLM_MODEL` - LLM model (optional)

### Server Environment Variables
- `SKILLHUB_SECURITY_SCANNER_ENABLED` - Enable scanner (default: false)
- `SKILLHUB_SECURITY_SCANNER_URL` - Scanner URL (default: http://skill-scanner:8000)
- `SKILLHUB_SECURITY_SCANNER_MODE` - Mode: local or upload (default: local)

## Troubleshooting

```bash
# Check scanner health from server
docker compose -p skillhub-staging exec server wget -qO- http://skill-scanner:8000/health

# Check shared volume
docker compose -p skillhub-staging exec server ls -la /tmp/skillhub-scans
docker compose -p skillhub-staging exec skill-scanner ls -la /tmp/skillhub-scans

# Rebuild scanner image
docker compose --profile scanner build skill-scanner
```

## Documentation

- [Scanner README](README.md) - Detailed usage guide
- [Integration Testing](../docs/scanner-integration-testing.md) - Test scenarios
- [Implementation Summary](../docs/17-scanner-docker-integration.md) - Architecture details
