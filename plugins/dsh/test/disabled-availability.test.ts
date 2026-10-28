import { expect, test } from 'bun:test';
import { inspectDisabledSkillAvailability, installationEntryName } from '../src/host/skill-availability.js';
import type { RemovalBaseline } from '../src/host/generated/native-contract.js';
import type { LoadedSkill } from '../src/host/skill-discovery.js';

function fixture() {
  const directory = '/skills/market-slug';
  const baseline: RemovalBaseline = { status: 'ready', kind: 'uninstall', inventoryItem: {
    registry: 'https://registry.example', namespace: 'demo', slug: 'market-slug', targets: [{ agent: 'dsh', rootDir: '/skills',
      installDir: directory, installedAt: 'now', version: '1', fingerprint: 'fp', installationState: 'disabled' }] },
    target: { path: directory, root: '/skills', scope: 'project', files: {}, metadata: { registry: 'https://registry.example', namespace: 'demo', slug: 'market-slug', version: '1' } } };
  const winner: LoadedSkill = { name: 'actual-name', description: 'fixture', content: 'content', source: 'user-dsh', provider: 'filesystem',
    invocation: { userInvocable: true, modelInvocable: true }, path: '/user/skills/copy/SKILL.md', resourceBase: { kind: 'directory', path: '/user/skills/copy' } };
  const input = { directory, fingerprint: 'fp', signal: new AbortController().signal, valid() {},
    baseline: async () => baseline, entry: async () => '---\nname: actual-name\ndescription: fixture\n---\nbody',
    reader: { snapshot: async () => ({ complete: true, skills: [{ name: 'actual-name' }] }), get: async (name: string): Promise<LoadedSkill | undefined> => {
      expect(name).toBe('actual-name'); return winner;
    } } };
  return { input, winner };
}

test('disabled diagnosis observes verified same-name fallback without authorizing use', async () => {
  const { input } = fixture();
  expect(await inspectDisabledSkillAvailability(input)).toMatchObject({ installationState: 'disabled', name: 'actual-name',
    integrity: 'verified', discovery: 'shadowed', reason: 'same-name-winner' });
  expect(await inspectDisabledSkillAvailability(input)).not.toHaveProperty('invocation');
});

test('watch-off cached selected winner remains observed separately from committed disabled state', async () => {
  const { input, winner } = fixture();
  input.reader.get = async () => ({ ...winner, path: '/skills/market-slug/SKILL.md', resourceBase: { kind: 'directory', path: '/skills/market-slug' } });
  expect(await inspectDisabledSkillAvailability(input)).toMatchObject({ installationState: 'disabled', discovery: 'winner', reason: 'installation-disabled' });
});

test('absence, partial snapshots and changing winners remain distinct', async () => {
  const { input } = fixture();
  input.reader.get = async () => undefined;
  input.reader.snapshot = async () => ({ complete: true, skills: [] });
  expect((await inspectDisabledSkillAvailability(input)).discovery).toBe('not-discovered');
  input.reader.snapshot = async () => ({ complete: false, skills: [] });
  expect(await inspectDisabledSkillAvailability(input)).toMatchObject({ discovery: 'unknown', reason: 'snapshot-incomplete' });
});

test('routing-name extraction refuses ambiguous YAML and never substitutes the package slug', () => {
  expect(installationEntryName('---\nname: actual-name\n---\ntext')).toBe('actual-name');
  for (const fields of ['name: one\nname: two', 'name: [wrong]', 'name: |\n  hidden', '<<: *defaults', '"name": hidden']) {
    expect(installationEntryName(`---\n${fields}\n---\ntext`)).toBeUndefined();
  }
});

test('a changed winner is unknown and an unresponsive reader is bounded', async () => {
  const { input, winner } = fixture();
  let calls = 0;
  input.reader.get = async () => ++calls === 1 ? winner : undefined;
  expect(await inspectDisabledSkillAvailability(input)).toMatchObject({ discovery: 'unknown', reason: 'observation-changed' });
  input.reader.snapshot = () => new Promise(() => {});
  const started = performance.now();
  expect(await inspectDisabledSkillAvailability(input)).toMatchObject({ installationState: 'disabled', discovery: 'unknown', reason: 'read-failed' });
  expect(performance.now() - started).toBeLessThan(3500);
}, 5000);
