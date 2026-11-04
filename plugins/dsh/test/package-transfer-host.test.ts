import { expect, test } from 'bun:test';
import { SkillHubService } from '../src/host/index.js';
import { ConfirmationPlans } from '../src/host/upgrade-plans.js';

function fixture() {
  const state = { previews: 0, stale: false };
  const service = Object.assign(Object.create(SkillHubService.prototype), {
    lifetime: new AbortController(), packageImports: new ConfirmationPlans(), packagePreviewBytes: 0,
    packageContext: () => ({ scope: { sessionId: 'session', cwd: '/project', sourceRevision: 'source' }, hostInstanceId: 'host', agent: { ctx: {} },
      valid: () => { if (state.stale) throw new Error('stale context'); }, runtime: {
        previewPackage: async () => { state.previews++; return { name: 'writer', summary: 'Write notes', files: 2, bytes: 120, fingerprint: 'sha256:test' }; },
      } }),
  });
  return { service, state, signal: new AbortController().signal };
}

test('upload rejects malformed, noncanonical, empty and oversized base64 before unpacking', async () => {
  const f = fixture();
  for (const data of ['', 'invalid', '!!!!', 'YQ=', 'YR==', 'YQ==\n', 'A'.repeat(11_184_816)]) {
    await expect(f.service.previewPackageImport('source', data, f.signal)).rejects.toThrow();
  }
  expect(f.state.previews).toBe(0);
});

test('package preview exposes no paths or archive and binds exact session/source context', async () => {
  const f = fixture();
  const preview = await f.service.previewPackageImport('source', 'YQ==', f.signal);
  expect(preview).toMatchObject({ name: 'writer', files: 2, bytes: 120 });
  expect(Object.keys(preview).sort()).toEqual(['bytes', 'expiresAt', 'files', 'name', 'previewId', 'summary']);
  expect(f.service.packageImports.read(preview.previewId, { sessionId: 'session', cwd: '/project', sourceRevision: 'source' }).archive).toEqual(new Uint8Array([97]));
  expect(() => f.service.packageImports.read(preview.previewId, { sessionId: 'other', cwd: '/project', sourceRevision: 'source' })).toThrow();
  expect(() => f.service.packageImports.read(preview.previewId, { sessionId: 'session', cwd: '/project', sourceRevision: 'changed' })).toThrow();
});

test('aborted preview never retains an installation confirmation', async () => {
  const f = fixture();
  const aborted = AbortSignal.abort();
  await expect(f.service.previewPackageImport('source', 'YQ==', aborted)).rejects.toThrow();
  expect(f.service.packagePreviewBytes).toBe(0);
});

test('late preview context invalidation prevents retaining upload', async () => {
  const f = fixture();
  f.state.stale = true;
  await expect(f.service.previewPackageImport('source', 'YQ==', f.signal)).rejects.toThrow('stale context');
  expect(f.service.packagePreviewBytes).toBe(0);
});
