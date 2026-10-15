import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import { mkdir, mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import SkillHubService from '@astron-team/skillhub-dsh';

test('upgrade recovery requires every historical target and revalidates all roots before each mutation', async () => {
  const home = await mkdtemp(join(tmpdir(), 'skillhub-upgrade-recovery-host-'));
  const previous = process.env.DSH_HOME;
  try {
    process.env.DSH_HOME = join(home, 'dsh');
    const cwd = join(home, 'project');
    await mkdir(join(cwd, '.git'), { recursive: true });
    const roots = [join(home, 'dsh/skills'), join(cwd, '.dsh/skills')];
    const fs = { sandboxMode: 'workspace-write', resolve: async (path: string) => path,
      processPath: (path: string) => path, processPathFromHostPath: (path: string) => path };
    let mode = 'danger-full-access';
    const agent = { session: { id: 'owner', header: { cwd } }, ctx: {
      get: (name: string) => name === 'fs' ? fs : { resolve: () => ({ mode, workspaceRoot: cwd }) },
    } };
    const operation = { operationId: randomUUID(), kind: 'upgrade', slug: 'writer',
      targetPaths: roots.map(root => join(root, 'writer')) };
    type Authorize = (paths: readonly string[]) => Promise<void>;
    let authorize: Authorize | undefined;
    const context = { ctx: { get: () => ({ get: () => agent }) }, localRuntime: {
      recoverOperation: async (_id: string, owner: string, prepare: (op: typeof operation) => Promise<Authorize>) => {
        expect(owner).toBe('owner');
        authorize = await prepare(operation);
      },
    } };
    const recover = () => Reflect.apply(SkillHubService.prototype.recoverInstallationOperation, context, ['owner', operation.operationId]);
    await recover();
    await authorize!(roots.map(root => join(root, '.writer.backup-fixture')));
    await expect(authorize!([join(home, 'foreign/writer')])).rejects.toThrow('escaped');
    mode = 'read-only';
    await expect(authorize!([join(roots[0]!, 'writer')])).rejects.toThrow();
    await expect(recover()).rejects.toThrow('target is unavailable');
    mode = 'danger-full-access';
    agent.session.header.cwd = home;
    await expect(authorize!([join(roots[0]!, 'writer')])).rejects.toThrow('session changed');
    agent.session.header.cwd = cwd;
    operation.targetPaths[1] = join(home, 'other-project/.dsh/skills/writer');
    await expect(recover()).rejects.toThrow('target is unavailable');
    operation.targetPaths[1] = operation.targetPaths[0]!;
    await expect(recover()).rejects.toThrow('Unsupported recovery target set');
  } finally {
    if (previous === undefined) delete process.env.DSH_HOME; else process.env.DSH_HOME = previous;
    await rm(home, { recursive: true, force: true });
  }
});
