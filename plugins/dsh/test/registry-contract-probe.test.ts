import { expect, test } from 'bun:test';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { probeRegistry } from '../scripts/probe-registry.js';

const { zipSync } = createRequire(new URL('../../../cli/package.json', import.meta.url))('fflate');
const content = '# Contract fixture';
const hash = createHash('sha256').update(content).digest('hex');
const fingerprint = 'sha256:' + createHash('sha256').update(`SKILL.md:${hash}\n`).digest('hex');

test.each(['valid', 'fingerprint-mismatch', 'unsafe-archive', 'resolve-denied', 'version-mismatch', 'optional-failure'] as const)(
  'anonymous registry contract probe: %s', async scenario => {
    const requests: string[] = [];
    const archive = zipSync({ [scenario === 'unsafe-archive' ? '../SKILL.md' : 'SKILL.md']: new TextEncoder().encode(content) });
    const server = Bun.serve({ hostname: '127.0.0.1', port: 0, fetch(request) {
      expect(request.method).toBe('GET');
      expect(request.headers.get('authorization')).toBeNull();
      expect(request.headers.get('cookie')).toBeNull();
      const url = new URL(request.url);
      requests.push(url.pathname);
      if (url.pathname.endsWith('/search')) return Response.json({ data: { items: [], total: 0, limit: 20 } });
      if (url.pathname.endsWith('/resolve')) {
        if (scenario === 'resolve-denied') return new Response('private diagnostics', { status: 403 });
        expect(url.searchParams.get('version')).toBe('1');
        return Response.json({ data: { namespace: 'team', slug: 'writer', version: scenario === 'version-mismatch' ? '2' : '1', versionId: 7,
          fingerprint: scenario === 'fingerprint-mismatch' ? 'sha256:wrong' : fingerprint, downloadUrl: '/download?signature=do-not-leak' } });
      }
      if (url.pathname === '/download') return new Response(archive, { headers: { 'content-type': 'application/zip' } });
      if (url.pathname.endsWith('/file')) return new Response(content);
      if (url.pathname.endsWith('/security-audit')) return new Response('private diagnostics', { status: 403 });
      if (url.pathname.endsWith('/versions') || url.pathname.endsWith('/reviews')) {
        if (scenario === 'optional-failure') return new Response('private diagnostics', { status: 503 });
        return Response.json({ data: { items: [], total: 0, page: 0, size: 20 } });
      }
      return Response.json({ data: { id: 42, namespace: 'team', slug: 'writer', displayName: null, summary: null } });
    } });
    try {
      const report = await probeRegistry({ registry: server.url.href, namespace: 'team', slug: 'writer', version: '1' });
      expect(report.requiredPassed).toBe(scenario === 'valid' || scenario === 'optional-failure');
      expect(JSON.stringify(report)).not.toContain('do-not-leak');
      expect(JSON.stringify(report)).not.toContain('private diagnostics');
      expect(report.checks).toHaveLength(8);
      if (scenario === 'valid' || scenario === 'resolve-denied') {
        const child = Bun.spawn(['bun', 'run', 'probe:registry', '--registry', server.url.href,
          '--namespace', 'team', '--slug', 'writer', '--version', '1'], {
          cwd: fileURLToPath(new URL('..', import.meta.url)), stdout: 'pipe', stderr: 'pipe',
        });
        const [stdout, stderr, exitCode] = await Promise.all([
          new Response(child.stdout).text(), new Response(child.stderr).text(), child.exited,
        ]);
        expect(exitCode).toBe(scenario === 'valid' ? 0 : 1);
        expect(JSON.parse(stdout).requiredPassed).toBe(scenario === 'valid');
        expect(stdout + stderr).not.toContain('do-not-leak');
        expect(stdout + stderr).not.toContain('private diagnostics');
      }
      if (scenario === 'resolve-denied' || scenario === 'version-mismatch') {
        expect(requests).not.toContain('/download');
        expect(report.checks.find(item => item.name === 'download-archive-fingerprint')?.outcome).toBe('blocked');
      } else expect(report.checks.find(item => item.name === 'security-audit-access')?.outcome).toBe('anonymous-denied');
      if (scenario === 'optional-failure') expect(report.checks.find(item => item.name === 'reviews'))
        .toMatchObject({ outcome: 'failed', reason: 'REGISTRY_UNAVAILABLE' });
    } finally { await server.stop(true); }
  },
);
