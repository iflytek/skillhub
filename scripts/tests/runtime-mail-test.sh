#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
python3 - "$ROOT" <<'PY'
import hashlib
import json
import os
import pathlib
import shutil
import stat
import subprocess
import sys
import tempfile

root = pathlib.Path(sys.argv[1])
script = pathlib.Path(os.environ.get('RUNTIME_SCRIPT', str(root / 'scripts/runtime.sh')))
real_docker = shutil.which('docker')
assert real_docker, 'Docker Compose is required for render-only verification'
checks = []
key = 'SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST'
password = 'fixture$pass # "quoted" value'

with tempfile.TemporaryDirectory(prefix='skillhub-runtime-mail-') as work:
    work = pathlib.Path(work)
    bin_dir = work / 'bin'
    bin_dir.mkdir()
    docker = bin_dir / 'docker'
    docker.write_text('''#!/usr/bin/env python3
import hashlib,json,os,pathlib,subprocess,sys
args=sys.argv[1:]
if args==['compose','version']:raise SystemExit(0)
assert args[0]=='compose'
flags=args[1:]
config=[]
while flags and flags[0] in ['--env-file','-f']:
 config.extend(flags[:2]);flags=flags[2:]
operation=flags[0]
render=json.loads(subprocess.check_output([os.environ['REAL_DOCKER'],'compose',*config,'config','--format','json'],stderr=subprocess.PIPE))
server=render['services']['server']['environment']
# Compose config serializes dollars as $$ so its output can be reused as input.
# Decode that serialization once; the dotenv source itself is checked byte-for-byte.
password=(server.get('SPRING_MAIL_PASSWORD') or '').replace('$$','$')
row={'operation':operation,'smtpHost':server.get('SPRING_MAIL_HOST'),'smtpPort':server.get('SPRING_MAIL_PORT'),'smtpSsl':server.get('SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE'),'smtpTrust':server.get('SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_TRUST'),'fromAddress':server.get('SKILLHUB_NOTIFICATION_EMAIL_FROM_ADDRESS'),'mailEnabled':server.get('SKILLHUB_NOTIFICATION_EMAIL_ENABLED'),'passwordHash':hashlib.sha256(password.encode()).hexdigest()}
with open(os.environ['DOCKER_LOG'],'a') as f:f.write(json.dumps(row)+'\\n')
if operation==os.environ.get('DOCKER_FAIL_ON'):raise SystemExit(55)
''')
    docker.chmod(0o755)
    curl = bin_dir / 'curl'
    curl.write_text('''#!/usr/bin/env python3
import os,pathlib,shutil,sys,urllib.parse
args=sys.argv[1:];src=args[args.index('-o')-1];dest=args[args.index('-o')+1]
source=pathlib.Path(urllib.parse.unquote(urllib.parse.urlparse(src).path))
if source.name==os.environ.get('DOWNLOAD_FAIL_BASENAME'):
 print('fixture download failure',file=sys.stderr);raise SystemExit(22)
shutil.copyfile(source,dest)
''')
    curl.chmod(0o755)
    awk = bin_dir / 'awk'
    awk.write_text('''#!/usr/bin/env python3
import os,stat,sys
assert stat.S_IMODE(os.fstat(sys.stdout.fileno()).st_mode)==0o600, 'migration temporary file must be exclusive and private'
os.execv(os.environ['REAL_AWK'], [os.environ['REAL_AWK'], *sys.argv[1:]])
''')
    awk.chmod(0o755)
    mktemp = bin_dir / 'mktemp'
    mktemp.write_text('''#!/usr/bin/env python3
import os,sys
if os.environ.get('FAIL_MKTEMP'):raise SystemExit(73)
os.execv(os.environ['REAL_MKTEMP'], [os.environ['REAL_MKTEMP'], *sys.argv[1:]])
''')
    mktemp.chmod(0o755)
    env = {**os.environ, 'PATH': str(bin_dir) + os.pathsep + os.environ['PATH'],
           'SKILLHUB_RAW_BASE': root.as_uri(), 'REAL_DOCKER': real_docker,
           'REAL_AWK': shutil.which('awk'), 'REAL_MKTEMP': shutil.which('mktemp')}
    for inherited in list(env):
        if inherited.startswith(('SPRING_MAIL_', 'SKILLHUB_NOTIFICATION_EMAIL_', 'POSTGRES_')) or inherited in ('SKILLHUB_HOME', 'SKILLHUB_VERSION', 'SKILLHUB_PUBLIC_BASE_URL'):
            env.pop(inherited, None)
    # The test never invokes a real lifecycle operation: only real Compose config.
    def run(home, command='up', extra=None, success=True):
        home.mkdir(exist_ok=True)
        log = home / 'calls.jsonl'
        current = {**env, 'DOCKER_LOG': str(log), **(extra or {})}
        result = subprocess.run(['sh', str(script), command, '--home', str(home)], env=current,
                                text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert (result.returncode == 0) is success, f'{command} unexpected exit {result.returncode}'
        assert password not in result.stdout + result.stderr, 'runtime output exposed fixture password'
        return result, [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
    def fixture(home, trust=None):
        home.mkdir(exist_ok=True)
        text = '\n'.join([
            'SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=fixture-existing-cookie-secret-at-least-32-bytes',
            'POSTGRES_PASSWORD=fixture-database-secret',
            'SPRING_MAIL_HOST=smtp.example.test', 'SPRING_MAIL_PORT=465',
            "SPRING_MAIL_PASSWORD='" + password + "'", 'SPRING_MAIL_USERNAME=sender@example.test',
            'SPRING_MAIL_SMTP_AUTH=true', 'SPRING_MAIL_SMTP_STARTTLS_ENABLE=false',
            'SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true',
            'SKILLHUB_NOTIFICATION_EMAIL_FROM_ADDRESS=sender@example.test',
            'SKILLHUB_NOTIFICATION_EMAIL_ENABLED=false',
            'SKILLHUB_NOTIFICATION_EMAIL_DIGEST_WINDOW_MINUTES=15',
            'SKILLHUB_NOTIFICATION_EMAIL_PUBLIC_BASE_URL=https://skills.example.test/skillhub',
            'SKILLHUB_PUBLIC_BASE_URL=https://skills.example.test/skillhub',
            'SKILLHUB_VERSION=fixture-pinned-version',
        ]) + '\n'
        if trust is not None:
            text += key + '=' + trust + '\n'
        (home / '.env.release').write_text(text)
        return text
    def configured(rows):
        assert rows, 'runtime never reached the Compose contract'
        row = rows[-1]
        assert row['smtpHost'] == 'smtp.example.test'
        assert str(row['smtpPort']) == '465'
        assert str(row['smtpSsl']).lower() == 'true'
        assert row['fromAddress'] == 'sender@example.test'
        assert row['passwordHash'] == hashlib.sha256(password.encode()).hexdigest(), 'fixture SMTP password changed during Compose rendering'
        assert str(row['mailEnabled']).lower() == 'false'
    first = work / 'first install'
    _, calls = run(first)
    assert stat.S_IMODE((first / '.env.release').stat().st_mode) == 0o600
    assert not calls[-1]['smtpHost']
    assert str(calls[-1]['mailEnabled']).lower() == 'false'
    assert calls[-1]['smtpTrust'] is None
    checks.append('fresh install without SMTP remains optional and private')

    home = work / 'configured'
    expected = fixture(home)
    for command in ['up', 'pull', 'down', 'ps', 'logs']:
        _, calls = run(home, command)
        assert (home / '.env.release').read_text() == expected
        assert stat.S_IMODE((home / '.env.release').stat().st_mode) == 0o600
        configured(calls)
    checks.append('up/pull/down/ps/logs preserve SMTP secrets, sender, bootstrap policy, public URL and pinned version')

    for index, value in enumerate(['', '   ', '""', "''", '"   "', "'   '", ' # old comment', '"" # old comment']):
        legacy = work / f'legacy-{index}'
        expected = fixture(legacy)
        with (legacy / '.env.release').open('a') as f:f.write(key + '=' + value + '\n')
        _, calls = run(legacy)
        assert (legacy / '.env.release').read_text() == expected
        assert calls[-1]['smtpTrust'] is None
        configured(calls)
        assert not (legacy / '.env.release.smtp-tmp').exists()
    checks.append('legacy empty/whitespace/quoted/commented trust overrides migrate without changing other values')

    for index, value in enumerate(['smtp.example.test', '"smtp.example.test" # approved exception', "'smtp.example.test'"]):
        explicit = work / f'explicit-{index}'
        expected = fixture(explicit, value)
        _, calls = run(explicit)
        assert (explicit / '.env.release').read_text() == expected
        assert calls[-1]['smtpTrust'] == 'smtp.example.test'
    checks.append('unquoted and quoted explicit trust hosts are preserved byte-for-byte')

    duplicate = work / 'duplicate-trust'
    fixture(duplicate, 'smtp.example.test')
    with (duplicate / '.env.release').open('a') as f:f.write('  export ' + key + ' = "" # legacy\n')
    _, calls = run(duplicate)
    assert calls[-1]['smtpTrust'] == 'smtp.example.test'
    assert key + '=smtp.example.test' in (duplicate / '.env.release').read_text()
    checks.append('explicit nonempty trust host remains intact when an empty duplicate is removed')

    for target_type in ['file', 'symlink']:
        protected = work / f'preexisting-temp-{target_type}'
        expected = fixture(protected)
        with (protected / '.env.release').open('a') as f:f.write(key + '=\n')
        stale = protected / '.env.release.smtp-tmp'
        victim = protected / 'unrelated-file'
        victim.write_text('unrelated marker\n')
        victim.chmod(0o644)
        if target_type == 'file':
            stale.write_text('existing temporary marker\n')
            stale.chmod(0o644)
        else:
            stale.symlink_to(victim)
        _, calls = run(protected)
        assert (protected / '.env.release').read_text() == expected
        assert stat.S_IMODE((protected / '.env.release').stat().st_mode) == 0o600
        assert victim.read_text() == 'unrelated marker\n'
        assert stat.S_IMODE(victim.stat().st_mode) == 0o644
        if target_type == 'file':
            assert stale.read_text() == 'existing temporary marker\n'
            assert stat.S_IMODE(stale.stat().st_mode) == 0o644
        else:
            assert stale.is_symlink()
        assert not list(protected.glob('.env.release.smtp.*')), 'private temporary file was not removed'
        configured(calls)
    checks.append('exclusive private temporary files do not overwrite existing broad-permission files or symlinks')

    shell_empty = work / 'shell-empty'
    fixture(shell_empty)
    _, calls = run(shell_empty, extra={key: '   '})
    assert calls[-1]['smtpTrust'] is None
    _, calls = run(shell_empty, extra={key: 'smtp.shell.example.test'})
    assert calls[-1]['smtpTrust'] == 'smtp.shell.example.test'
    checks.append('blank inherited shell trust is omitted and explicit inherited host is preserved')

    failed_temp = work / 'failed-temp'
    original = fixture(failed_temp, '')
    _, calls = run(failed_temp, extra={'FAIL_MKTEMP': '1'}, success=False)
    assert not calls, 'temporary-file failure reached Docker'
    assert (failed_temp / '.env.release').read_text() == original
    assert not list(failed_temp.glob('.env.release.smtp.*'))
    run(failed_temp)
    checks.append('temporary-file allocation failure leaves original configuration intact and supports retry')

    failed_download = work / 'failed-download'
    original = fixture(failed_download, '')
    for fail in ['compose.release.yml', '.env.release.example']:
        log = failed_download / 'calls.jsonl'
        if log.exists():log.unlink()
        _, calls = run(failed_download, 'up', extra={'DOWNLOAD_FAIL_BASENAME': fail}, success=False)
        assert not calls, 'download failure reached Docker'
        assert (failed_download / '.env.release').read_text() == original
    run(failed_download, 'up')
    assert key + '=' not in (failed_download / '.env.release').read_text()
    checks.append('download failure leaves SMTP config untouched and a successful retry completes migration')

    failed_docker = work / 'failed-docker'
    expected = fixture(failed_docker)
    for command, operation in [('up', 'up'), ('pull', 'pull'), ('down', 'down')]:
        run(failed_docker, command, extra={'DOCKER_FAIL_ON': operation}, success=False)
        assert (failed_docker / '.env.release').read_text() == expected
        run(failed_docker, command)
        assert (failed_docker / '.env.release').read_text() == expected
    checks.append('Docker up/pull/down failures propagate and retry preserves the same SMTP configuration')

print(json.dumps({'status': 'passed', 'checks': checks, 'real_compose_render': True,
                  'real_services_started': False, 'real_smtp_used': False}, indent=2))
PY
