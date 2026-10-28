import { expect, test } from 'bun:test';
import { selectUsableInstallation } from '../src/host/skill-use-selection.js';
import type { InstalledSkillList, SkillUsePreparation } from '../src/host/types.js';

const item = (itemKey: string, scope: 'user' | 'project' = 'user'): InstalledSkillList['items'][number] => ({
  itemKey, scope, namespace: 'demo', slug: 'helper', version: '1.0.0', state: 'present', installationState: 'enabled',
});
const ready: SkillUsePreparation = { status: 'ready', name: 'actual-name', version: '1.0.0', scope: 'user', examples: { state: 'none' } };
const blocked = (reason: Extract<SkillUsePreparation, { status: 'blocked' }>['reason']): SkillUsePreparation => ({ status: 'blocked', reason });
function input(items = [item('user'), item('project', 'project')]) {
  return { namespace: 'demo', slug: 'helper', signal: new AbortController().signal, valid() {},
    list: async (): Promise<InstalledSkillList> => ({ items, complete: true, unavailable: [], issues: [] }),
    prepare: async (key: string): Promise<SkillUsePreparation> => key === 'user' ? ready : blocked('skill-shadowed'),
  };
}

test('selects the actual ready user even with a newer project first, filtering coordinates', async () => {
  const options = input([{ ...item('other'), slug: 'different' }, { ...item('project', 'project'), version: '99.0.0' }, item('user')]);
  const calls: string[] = [];
  const prepare = options.prepare;
  options.prepare = async key => { calls.push(key); return prepare(key); };
  expect(await selectUsableInstallation(options)).toEqual({ status: 'ready', itemKey: 'user' });
  expect(calls.filter(key => key === 'user')).toHaveLength(2);
  expect(calls).not.toContain('other');
});

test('disabled siblings are excluded but unknown, modified, missing, and pending siblings block guessing', async () => {
  for (const reason of ['installation-disabled', 'files-changed', 'installation-unverified', 'operation-pending', 'skill-unavailable'] as const) {
    const options = input(); options.prepare = async key => key === 'user' ? ready : blocked(reason);
    expect(await selectUsableInstallation(options)).toEqual(reason === 'installation-disabled'
      ? { status: 'ready', itemKey: 'user' } : { status: 'blocked', reason: 'selection-unverified' });
  }
});

test('either scope can be the sole usable copy while its sibling is disabled', async () => {
  for (const scope of ['user', 'project'] as const) {
    const options = input();
    options.prepare = async key => key === scope ? { ...ready, scope } : blocked('installation-disabled');
    expect(await selectUsableInstallation(options)).toEqual({ status: 'ready', itemKey: scope });
  }
  expect(await selectUsableInstallation(input([item('user')]))).toEqual({ status: 'ready', itemKey: 'user' });
});

test('zero and multiple usable copies never choose an arbitrary installation', async () => {
  expect(await selectUsableInstallation(input([]))).toEqual({ status: 'blocked', reason: 'no-installation' });
  const options = input(); options.prepare = async key => ({ ...ready, name: `actual-${key}` });
  expect(await selectUsableInstallation(options)).toEqual({ status: 'blocked', reason: 'multiple-usable-installations' });
  options.prepare = async () => blocked('user-invocation-disabled');
  expect(await selectUsableInstallation(options)).toEqual({ status: 'blocked', reason: 'no-usable-installation' });
});

test('incomplete inventory, unavailable roots, and changing candidates fail closed', async () => {
  for (const patch of [{ complete: false }, { unavailable: ['project'] as const }, { issues: ['invalid'] }]) {
    const options = input(); const list = options.list;
    options.list = async () => ({ ...await list(), ...patch, unavailable: [...(patch.unavailable ?? [])] });
    expect(await selectUsableInstallation(options)).toEqual({ status: 'blocked', reason: 'selection-unverified' });
  }
  const options = input(); const list = options.list; let reads = 0;
  options.list = async () => ({ ...await list(), items: ++reads === 1 ? [item('user')] : [item('changed')] });
  expect(await selectUsableInstallation(options)).toEqual({ status: 'blocked', reason: 'selection-unverified' });
});

test('winner changes between passes cannot become a stable selection', async () => {
  const options = input(); let calls = 0;
  options.prepare = async key => (++calls <= 2 ? key === 'user' : key === 'project') ? ready : blocked('skill-shadowed');
  expect(await selectUsableInstallation(options)).toEqual({ status: 'blocked', reason: 'selection-unverified' });
});

test('bounds parallel reads and aborts a stalled provider without waiting for it', async () => {
  const options = input(Array.from({ length: 8 }, (_, i) => item(String(i))));
  let active = 0; let max = 0;
  options.prepare = async () => { active++; max = Math.max(max, active); await new Promise(resolve => setTimeout(resolve, 1)); active--; return blocked('installation-disabled'); };
  await selectUsableInstallation(options); expect(max).toBe(4);
  options.prepare = async () => new Promise(() => {});
  expect(await selectUsableInstallation(options, 10)).toEqual({ status: 'blocked', reason: 'selection-timeout' });
  const abort = new AbortController(); options.signal = abort.signal;
  const pending = selectUsableInstallation(options); abort.abort();
  await expect(pending).rejects.toThrow();
});

test('a late inventory response after timeout never starts candidate checks', async () => {
  const options = input(); const list = options.list;
  let release!: () => void; let checks = 0;
  const delayed = new Promise<void>(resolve => { release = resolve; });
  options.list = async () => { await delayed; return list(); };
  options.prepare = async () => { checks++; return ready; };
  expect(await selectUsableInstallation(options, 10)).toEqual({ status: 'blocked', reason: 'selection-timeout' });
  release(); await new Promise(resolve => setTimeout(resolve, 0));
  expect(checks).toBe(0);
});
