import { expect, test } from 'bun:test';
import { randomUUID } from 'node:crypto';
import SkillHubService from '@astron-team/skillhub-dsh';

test('recovery Remote binds host and owner, rejects raw extra fields and never creates missing operations', async () => {
  const input = { operationId: randomUUID(), hostInstanceId: randomUUID() };
  let owner = 'session';
  let recovered = 0;
  let kind = 'uninstall';
  let fail = false;
  const context = {
    ctx: { invocation: { request: { args: { agentId: 'session', input: input as object } } },
      get: () => ({ get: () => ({ session: { id: 'session' } }) }) },
    localRuntime: {
      getStatus: () => ({ state: 'ready', hostInstanceId: input.hostInstanceId }),
      getOperation: async (id: string, session: string) => id === input.operationId && session === owner
        ? { operationId: id, kind, phase: recovered ? 'failed' : 'recovery-required', recovery: recovered ? 'restored' : 'pending' } : undefined,
    },
    getOperation: SkillHubService.prototype.getOperation,
    recoverInstallationOperation: async (session: string, id: string) => {
      expect(session).toBe(owner);
      expect(id).toBe(input.operationId);
      if (fail) throw new Error('/private/host/path');
      recovered++;
    },
  };
  const call = (wire: object = input) => {
    context.ctx.invocation.request.args.input = wire;
    return Reflect.apply(SkillHubService.prototype.recoverOperation, context, [input]);
  };
  await expect(call({ ...input, path: '/arbitrary' })).rejects.toThrow('Invalid recovery request');
  await expect(call({ ...input, hostInstanceId: randomUUID() })).rejects.toThrow('Host identity changed');
  expect(await call({ ...input, operationId: randomUUID() })).toBeNull();
  owner = 'another-session';
  expect(await call()).toBeNull();
  owner = 'session';
  kind = 'unsupported';
  await expect(call()).rejects.toThrow('unavailable for this operation kind');
  expect(recovered).toBe(0);
  kind = 'uninstall';
  fail = true;
  await expect(call()).rejects.toThrow('Recovery could not be confirmed');
  fail = false;
  expect(await call()).toMatchObject({ operationId: input.operationId, recovery: 'restored' });
  expect(await call()).toMatchObject({ operationId: input.operationId, recovery: 'restored' });
});
