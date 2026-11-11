import { readFile } from 'node:fs/promises';
import { join } from 'node:path';

/** Execute exactly once through the selected official Host API, never retry a mutation to detect its shape. */
export async function executeFixtureTool(ctx, root, input) {
  const manifest = JSON.parse(await readFile(join(root, 'node_modules/@deepseek-ai/dsh/package.json'), 'utf8'));
  if (manifest.name !== '@deepseek-ai/dsh') throw new Error('Fixture Host package identity is unavailable.');
  if (['0.2.0-rc.2', '0.2.1-rc.2', '0.2.1-rc.5'].includes(manifest.version)) return ctx.tools.execute(input);
  if (manifest.version !== '0.2.2-rc.1') throw new Error('Fixture tool execution requires an explicitly supported DSH release.');
  const args = JSON.stringify(input.arguments);
  if (typeof args !== 'string') throw new Error('Fixture tool arguments must be JSON serializable.');
  const batch = await ctx.tools.execute({
    calls: [{ type: 'tool-call', id: input.callId, name: input.name, arguments: args }],
    agent: input.agent, signal: input.signal, ...(input.toolSet ? { toolSet: input.toolSet } : {}),
  });
  if (!Array.isArray(batch?.results) || batch.results.length !== 1 || batch.results[0]?.callId !== input.callId
    || typeof batch.results[0]?.result?.isError !== 'boolean') throw new Error('Fixture tool batch did not return the exact call result.');
  return batch.results[0].result;
}
