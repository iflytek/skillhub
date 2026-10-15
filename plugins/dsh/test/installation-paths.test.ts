import { expect, test } from 'bun:test';
import { FsTargetKey } from '@deepseek-ai/dsh-fs';
import { mkdtemp, mkdir, rm, symlink, writeFile, readdir } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { assertInstallationMutation, assertInstallationPolicy, assertRemovalMutation, resolveInstallationPath, resolveInstalledPath } from '../src/host/installation-paths.js';
import { randomUUID } from 'node:crypto';

const local = {
  sandboxMode: undefined,
  processPathFromHostPath: (path: string) => path,
  resolve: async (path: string) => ({ targetKey: FsTargetKey('opaque'), displayPath: path }),
  processPath: (target: { displayPath: string }) => target.displayPath,
};

test('removal authorizes only the confirmed target and operation quarantine under current policy', async () => {
  const home = await mkdtemp(join(tmpdir(), 'skillhub-removal-paths-'));
  try {
    const root = join(home, 'skills');
    const trash = join(home, '.skillhub-trash');
    const id = randomUUID();
    const quarantine = join(trash, id);
    await mkdir(root);
    await mkdir(trash);
    await assertRemovalMutation(root, 'writer', id, [root, join(root, 'writer'), trash, quarantine]);
    for (const path of [home, join(root, 'other'), join(trash, randomUUID()), join(quarantine, 'SKILL.md')]) {
      await expect(assertRemovalMutation(root, 'writer', id, [path])).rejects.toThrow('escaped');
    }
    await expect(assertRemovalMutation(root, '../writer', id, [root])).rejects.toThrow('identity');
    await expect(assertRemovalMutation(root, 'writer', 'invalid', [root])).rejects.toThrow('identity');
    await expect(assertRemovalMutation(root, 'writer', id, [quarantine], { mode: 'read-only', workspaceRoot: home })).rejects.toThrow('read-only');
    await mkdir(join(home, 'foreign'));
    await symlink(join(home, 'foreign'), quarantine, 'junction');
    await expect(assertRemovalMutation(root, 'writer', id, [quarantine])).rejects.toThrow('escaped');
  } finally { await rm(home, { recursive: true, force: true }); }
});

test('default roots use custom DSH home and nearest Git worktree without creating skill directories', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-targets-'));
  const previous = process.env.DSH_HOME;
  try {
    process.env.DSH_HOME = join(root, 'custom');
    const cwd = join(root, 'project', 'nested');
    await mkdir(cwd, { recursive: true });
    await writeFile(join(root, 'project', '.git'), 'gitdir: /fixture');
    const options = { cwd, fs: local, signal: new AbortController().signal };
    expect((await resolveInstallationPath({ ...options, scope: 'project' })).root).toBe(join(root, 'project', '.dsh', 'skills'));
    expect((await resolveInstallationPath({ ...options, scope: 'user' })).root).toBe(join(root, 'custom', 'skills'));
    expect((await resolveInstallationPath({ ...options, scope: 'project', fs: { ...local, sandboxMode: 'workspace-write' },
      policy: { mode: 'workspace-write', workspaceRoot: cwd } })).root).toBe(join(root, 'project', '.dsh', 'skills'));
    await expect(resolveInstallationPath({ ...options, scope: 'project', fs: { ...local, sandboxMode: 'workspace-write' },
      policy: { mode: 'read-only', workspaceRoot: cwd } })).rejects.toThrow('read-only');
    expect((await resolveInstalledPath({ ...options, scope: 'project', fs: { ...local, sandboxMode: 'read-only' } })).root)
      .toBe(join(root, 'project', '.dsh', 'skills'));
    expect(await readdir(root)).toEqual(['project']);
    expect(await readdir(join(root, 'project'))).toEqual(['.git', 'nested']);
    await rm(join(root, 'project', '.git'));
    expect((await resolveInstallationPath({ ...options, scope: 'project' })).root).toBe(join(cwd, '.dsh', 'skills'));
  } finally {
    if (previous === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = previous;
    await rm(root, { recursive: true, force: true });
  }
});

test('effective policy denies read-only and sibling prefixes, permits workspace and official temporary roots', async () => {
  const workspaceRoot = join(process.cwd(), 'test-policy-workspace');
  const policy = { mode: 'workspace-write' as const, workspaceRoot };
  await assertInstallationPolicy(join(workspaceRoot, '.dsh', 'skills'), policy);
  await assertInstallationPolicy(join(tmpdir(), 'skillhub-policy-fixture'), policy);
  await expect(assertInstallationPolicy(workspaceRoot + '-other/skills', policy)).rejects.toThrow('outside');
  await expect(assertInstallationPolicy(workspaceRoot, { ...policy, mode: 'read-only' })).rejects.toThrow('read-only');
  await assertInstallationPolicy(workspaceRoot + '-other/skills', { ...policy, mode: 'danger-full-access' });
});

test('mutation guard rejects paths outside the accepted root and child symlink redirects', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-mutation-'));
  try {
    await mkdir(join(root, 'skills'));
    await mkdir(join(root, 'elsewhere'));
    const accepted = join(root, 'skills');
    await assertInstallationMutation(accepted, [accepted, join(accepted, '.writer.install-staging'), join(accepted, 'writer')]);
    await expect(assertInstallationMutation(accepted, [join(root, 'elsewhere')])).rejects.toThrow('escaped');
    await symlink(join(root, 'elsewhere'), join(accepted, 'writer'), 'junction');
    await expect(assertInstallationMutation(accepted, [join(accepted, 'writer')])).rejects.toThrow('escaped');
    await expect(assertInstallationMutation(accepted, [accepted], { mode: 'read-only', workspaceRoot: root })).rejects.toThrow('read-only');
  } finally { await rm(root, { recursive: true, force: true }); }
});

test('redirected roots, non-host mappings, cancelled and relative contexts are rejected', async () => {
  const root = await mkdtemp(join(tmpdir(), 'skillhub-targets-'));
  const previous = process.env.DSH_HOME;
  try {
    process.env.DSH_HOME = join(root, 'home');
    await mkdir(join(root, 'elsewhere'));
    await symlink(join(root, 'elsewhere'), join(root, '.dsh'), 'junction');
    const options = { cwd: root, fs: local, signal: new AbortController().signal, scope: 'project' as const };
    for (const sandboxMode of ['read-only', 'workspace-write', 'danger-full-access'] as const) {
      await expect(resolveInstallationPath({ ...options, fs: { ...local, sandboxMode } })).rejects.toThrow('policy-aware');
    }
    await expect(resolveInstallationPath(options)).rejects.toThrow('redirects');
    await expect(resolveInstallationPath({ ...options, cwd: 'relative' })).rejects.toThrow('absolute');
    await expect(resolveInstallationPath({ ...options, fs: { ...local, processPathFromHostPath: () => undefined } })).rejects.toThrow('does not share');
    await expect(resolveInstallationPath({ ...options, fs: { ...local, processPath: () => '/another' } })).rejects.toThrow('different Host');
    await expect(resolveInstallationPath({ ...options, signal: AbortSignal.abort() })).rejects.toThrow();
  } finally {
    if (previous === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = previous;
    await rm(root, { recursive: true, force: true });
  }
});
