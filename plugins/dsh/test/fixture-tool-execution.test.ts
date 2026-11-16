import { expect, test } from 'bun:test';
import { mkdtemp, mkdir, writeFile, rm } from 'node:fs/promises';
import { join } from 'node:path';
import { tmpdir } from 'node:os';
// @ts-expect-error Development-only observer helper has no TypeScript declarations.
import { executeFixtureTool } from './integration/fixture-tool-execution.mjs';

async function fixture(run: (root: string, version: (release: string) => Promise<void>) => Promise<void>) {
  const root = await mkdtemp(join(tmpdir(), 'fixture-tool-execution-'));
  const target = join(root, 'node_modules/@deepseek-ai/dsh');
  await mkdir(target, { recursive: true });
  try { await run(root, release => writeFile(join(target, 'package.json'), JSON.stringify({ name: '@deepseek-ai/dsh', version: release }))); }
  finally { await rm(root, { recursive: true, force: true }); }
}
const singleApi = { schemas() {}, executionMode() {} };
// Public ToolsService surface in the rc.1/rc.3 batch Host; there is no modelSchema method.
const batchApi = { schemas() {}, resolve() {}, setExecutor() {} };
const input = { callId: 'exact-call', name: 'test_tool', arguments: { text: 'content', nested: [1, true] }, agent: {}, signal: new AbortController().signal };

test('retained official Hosts use the original single-call interface exactly once', async () => {
  await fixture(async (root, version) => {
    for (const release of ['0.2.0-rc.2', '0.2.1-rc.2', '0.2.1-rc.5']) {
      await version(release); let calls = 0;
      const result = { isError: false, value: 'old-result' };
      expect(await executeFixtureTool({ tools: { ...singleApi, execute: async (value: unknown) => { calls++; expect(value).toBe(input); return result; } } }, root, input)).toBe(result);
      expect(calls).toBe(1);
    }
  });
});

test('batch Host schema/resolver/executor surface selects one raw call batch without a modelSchema method', async () => {
  await fixture(async (root, version) => {
    await version('0.2.2-rc.3'); let calls = 0;
    const result = { isError: true, error: { message: 'a real tool failure' }, content: [] };
    const ctx = { tools: { ...batchApi, execute: async (value: unknown) => {
      calls++; expect(value).toEqual({ calls: [{ type: 'tool-call', id: input.callId, name: input.name, arguments: JSON.stringify(input.arguments) }], agent: input.agent, signal: input.signal });
      return { results: [{ callId: input.callId, result }], additionalContexts: [], concluded: false };
    } } };
    expect(await executeFixtureTool(ctx, root, input)).toBe(result); expect(calls).toBe(1);
  });
});

test('missing tool contracts are rejected before execution and failed or malformed batches never trigger retries', async () => {
  await fixture(async (root, version) => {
    let calls = 0;
    const execute = async () => { calls++; throw new Error('batch failed'); };
    await version('0.2.2-rc.4');
    await expect(executeFixtureTool({ tools: { execute } }, root, input)).rejects.toThrow('contract is unavailable'); expect(calls).toBe(0);
    await version('0.2.2-rc.1');
    await expect(executeFixtureTool({ tools: { ...batchApi, execute } }, root, input)).rejects.toThrow('batch failed'); expect(calls).toBe(1);
    for (const results of [[], [{ callId: 'wrong', result: { isError: false } }], [{ callId: input.callId, result: {} }], [{ callId: input.callId, result: { isError: false } }, { callId: input.callId, result: { isError: false } }]]) {
      const before = calls;
      await expect(executeFixtureTool({ tools: { ...batchApi, execute: async () => { calls++; return { results }; } } }, root, input)).rejects.toThrow('exact call result');
      expect(calls).toBe(before + 1);
    }
  });
});


test('future Hosts use their observed batch contract instead of an RC allowlist', async () => {
  await fixture(async (root, version) => {
    for (const release of ['0.2.2-rc.4', '0.2.2-rc.5', '0.3.0', '1.0.0']) {
      await version(release); let calls = 0;
      const result = { isError: false, value: release };
      const ctx = { tools: { ...batchApi, execute: async () => {
        calls++;
        return { results: [{ callId: input.callId, result }] };
      } } };
      expect(await executeFixtureTool(ctx, root, input)).toBe(result);
      expect(calls).toBe(1);
    }
  });
});
