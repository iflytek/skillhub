import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { parseArgs } from 'node:util';
import { RegistryAdapter, RegistryError } from '../src/host/registry-adapter.js';
import { extractZip } from '../../../cli/src/platform/archive.ts';
import { readBoundedResponseBody } from '../../../cli/src/platform/download.ts';
import { snapshotSkillDirectory } from '../../../cli/src/services/skill-fingerprint.ts';

interface Check {
  name: string;
  required: boolean;
  outcome: 'passed' | 'failed' | 'blocked' | 'anonymous-denied' | 'public-response-unvalidated';
  reason?: string;
}

/** Anonymous read-only contract probe; extracted packages live only in a disposable directory. */
export async function probeRegistry(options: { registry: string; namespace: string; slug: string; version: string }, fetchImpl: typeof fetch = fetch) {
  if (![options.namespace, options.slug, options.version].every(value => value.trim().length > 0)) throw new Error('Coordinates and exact version are required');
  const registry = new RegistryAdapter(options.registry, fetchImpl);
  const checks: Check[] = [];
  async function check<T>(name: string, required: boolean, run: (signal: AbortSignal) => Promise<T>): Promise<T | undefined> {
    try {
      const result = await run(AbortSignal.timeout(30_000));
      checks.push({ name, required, outcome: 'passed' });
      return result;
    } catch (error) {
      checks.push({ name, required, outcome: !required && error instanceof RegistryError && error.code === 'ANONYMOUS_DENIED'
        ? 'anonymous-denied' : 'failed', reason: error instanceof RegistryError ? error.code : 'CHECK_FAILED' });
      return undefined;
    }
  }
  const { namespace, slug, version } = options;
  await check('search', true, signal => registry.search(slug, signal));
  const resolved = await check('resolve-exact-version', true, signal => registry.resolve(namespace, slug, version, signal));
  const metadata = await check('metadata', true, signal => registry.metadata(namespace, slug, signal));
  await check('documentation', true, signal => registry.documentation(namespace, slug, version, signal));
  await check('versions', false, signal => registry.versions(namespace, slug, 0, 20, signal));
  if (metadata) await check('reviews', false, signal => registry.reviews(metadata.id, 0, 20, signal));
  else checks.push({ name: 'reviews', required: false, outcome: 'blocked', reason: 'METADATA_UNAVAILABLE' });

  if (resolved) {
    await check('download-archive-fingerprint', true, async signal => {
      const url = new URL(resolved.downloadUrl, `${registry.source}/`);
      if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) throw new Error('Invalid archive URL');
      const response = await fetchImpl(url, { method: 'GET', credentials: 'omit', signal });
      let directory: string | undefined;
      try {
        if (!response.ok) throw new RegistryError(response.status === 401 || response.status === 403 ? 'ANONYMOUS_DENIED' : 'HTTP_ERROR', false, response.status);
        const buffer = await readBoundedResponseBody(response);
        directory = await mkdtemp(join(tmpdir(), 'skillhub-registry-contract-'));
        await extractZip(buffer, directory);
        const snapshot = await snapshotSkillDirectory(directory);
        if (!snapshot.files['SKILL.md'] || snapshot.fingerprint !== resolved.fingerprint) throw new RegistryError('INVALID_RESPONSE', false);
      } finally {
        await response.body?.cancel().catch(() => undefined);
        if (directory) await rm(directory, { recursive: true, force: true });
      }
    });
  } else checks.push({ name: 'download-archive-fingerprint', required: true, outcome: 'blocked', reason: 'RESOLVE_UNAVAILABLE' });

  if (metadata && resolved) {
    // Do not equate an HTTP 200 with a validated public audit schema or weaken server policy.
    try {
      const response = await fetchImpl(`${registry.source}/api/v1/skills/${metadata.id}/versions/${resolved.versionId}/security-audit`,
        { method: 'GET', credentials: 'omit', redirect: 'error', signal: AbortSignal.timeout(30_000) });
      await response.body?.cancel();
      checks.push({ name: 'security-audit-access', required: false,
        outcome: response.status === 401 || response.status === 403 ? 'anonymous-denied'
          : response.ok ? 'public-response-unvalidated' : 'failed', reason: `HTTP_${response.status}` });
    } catch { checks.push({ name: 'security-audit-access', required: false, outcome: 'failed', reason: 'REQUEST_FAILED' }); }
  } else checks.push({ name: 'security-audit-access', required: false, outcome: 'blocked', reason: 'IDENTITY_UNAVAILABLE' });
  return { schemaVersion: 1, checkedAt: new Date().toISOString(), registry: registry.source,
    skill: { namespace, slug, version }, requiredPassed: checks.filter(item => item.required).every(item => item.outcome === 'passed'), checks };
}

if (import.meta.main) {
  try {
    const { values } = parseArgs({ args: process.argv.slice(2), options: {
      registry: { type: 'string' }, namespace: { type: 'string' }, slug: { type: 'string' }, version: { type: 'string' }, output: { type: 'string' },
    }, strict: true, allowPositionals: false });
    if (!values.registry || !values.namespace || !values.slug || !values.version) {
      throw new Error('Required flags: --registry --namespace --slug --version; optional --output');
    }
    const report = await probeRegistry({ registry: values.registry, namespace: values.namespace, slug: values.slug, version: values.version });
    const json = JSON.stringify(report, null, 2);
    if (values.output) await writeFile(values.output, `${json}\n`, { flag: 'wx' });
    console.log(json);
    if (!report.requiredPassed) process.exitCode = 1;
  } catch {
    console.error('Registry probe failed. Check required flags, source URL and a new writable output path; credentials are not accepted.');
    process.exitCode = 1;
  }
}
