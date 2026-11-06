#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
python3 - "$REPO_ROOT" <<'CHECK'
import json, os, pathlib, subprocess, sys, tempfile
root = pathlib.Path(sys.argv[1])
key = 'SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST'
env = {**os.environ, 'SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET': 'smtp-contract-non-production-cookie-secret'}
env.pop(key, None)
with tempfile.TemporaryDirectory() as work:
    config = pathlib.Path(work) / 'smtp.env'
    def render(content):
        config.write_text(content)
        output = subprocess.check_output(['docker', 'compose', '--env-file', str(config),
            '-f', str(root / 'compose.release.yml'), 'config', '--format', 'json'], cwd=root, env=env)
        return json.loads(output)['services']['server']['environment']
    assert render('').get(key) is None, 'unset trust must not become an empty JavaMail property'
    example = (root / '.env.release.example').read_text()
    assert not any(line.startswith(key + '=') for line in example.splitlines()), 'default example must omit trust override'
    assert render(key + '=smtp.example.test\n')[key] == 'smtp.example.test', 'explicit host remains configurable'
    env[key] = 'smtp.shell.example.test'
    assert render('')[key] == 'smtp.shell.example.test', 'shell environment remains supported'
print('SMTP Compose default omission and explicit trust configuration passed')
CHECK
