# Dev Runtime Scripts

Current local runtime shortcut for:

- MySQL
- `local-file-index`
- memory runtime state
- UASS mock login page on `3001`

Usage:

```bash
scripts/dev/local-mysql-local-index-memory-up.sh
scripts/dev/local-mysql-local-index-memory-status.sh
scripts/dev/local-mysql-local-index-memory-down.sh
```

Ports:

- Web: `http://127.0.0.1:3000`
- Mock UASS: `http://127.0.0.1:3001/mock-uass`
- API: `http://127.0.0.1:8080`

Logs:

- `/tmp/skillhub-logs/web-3000.log`
- `/tmp/skillhub-logs/web-3001.log`
- `/tmp/skillhub-logs/backend-local-mysql-local-index-memory.log`
