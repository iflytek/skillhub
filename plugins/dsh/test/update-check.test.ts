import { expect, test } from 'bun:test';
import SkillHubService from '@astron-team/skillhub-dsh';

test('update check revalidates local identity and only resolves remote metadata', async () => {
  const installed = { namespace: 'demo', slug: 'writer', version: '1.0.0', fingerprint: 'original', state: 'present' };
  let current = { ...installed };
  let reads = 0;
  let resolves = 0;
  let assessments = 0;
  let onResolve = () => {};
  let onAssess = () => {};
  const lifetime = new AbortController();
  const context = {
    lifetime,
    async readInstalledItem() { reads++; return { item: { ...current }, valid() {} }; },
    async readSource(_revision: string, signal: AbortSignal, resolve: (registry: object, signal: AbortSignal) => unknown) {
      return resolve({ async resolve(namespace: string, slug: string, version: unknown) {
        resolves++;
        expect([namespace, slug, version]).toEqual(['demo', 'writer', undefined]);
        onResolve();
        return { version: '1.1.0', fingerprint: 'candidate' };
      } }, signal);
    },
    localRuntime: { async assessUpdate(local: typeof installed, candidate: { version: string; fingerprint: string }) {
      assessments++; onAssess();
      expect(local.fingerprint).toBe('original');
      return { state: 'upgradable', reason: 'newer-version', currentVersion: local.version, candidateVersion: candidate.version };
    } },
  };
  const check = (signal = new AbortController().signal) =>
    Reflect.apply(SkillHubService.prototype.checkUpdate, context, ['source', 'item', signal]);
  expect(await check()).toMatchObject({ state: 'upgradable', currentVersion: '1.0.0', candidateVersion: '1.1.0' });
  expect([reads, resolves, assessments]).toEqual([2, 1, 1]);

  current.state = 'missing';
  expect(await check()).toMatchObject({ state: 'blocked', reason: 'local-record-unverified' });
  expect(resolves).toBe(1);

  current = { ...installed };
  onResolve = () => { current.fingerprint = 'concurrently-changed'; };
  await expect(check()).rejects.toThrow('Installed record changed');
  expect(assessments).toBe(1);

  current = { ...installed };
  onResolve = () => { throw new Error('offline'); };
  expect(await check()).toMatchObject({ state: 'unavailable', candidateVersion: null });
  expect(assessments).toBe(1);

  onResolve = () => {};
  const controller = new AbortController();
  onAssess = () => { controller.abort(); };
  await expect(check(controller.signal)).rejects.toThrow();
  onAssess = () => { lifetime.abort(); };
  await expect(check()).rejects.toThrow();
});
