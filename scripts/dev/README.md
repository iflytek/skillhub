# Dev Runtime Scripts

Current local runtime shortcut for the `dev` profile:

- MySQL
- `mysql-like`
- memory runtime state

Usage:

```bash
scripts/dev/dev-up.sh
scripts/dev/dev-status.sh
scripts/dev/dev-down.sh
```

Ports:

- Web: `http://127.0.0.1:3000`
- API: `http://127.0.0.1:8080`

Logs:

- `/tmp/skillhub-logs/web-3000.log`
- `/tmp/skillhub-logs/backend-dev.log`
