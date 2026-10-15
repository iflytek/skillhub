// Read existing completed fixture operations after restarting the Host; never submit a write.
import assert from 'node:assert/strict';
import { readdir, readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
export const inject = ['skillhub', 'sessionController', 'typertGateway'];
export function apply(ctx, config) {
  assert.equal(process.env.DSH_HOME, join(config.root, 'home'));
  const timer = setTimeout(() => {
    run(ctx, config.root).then(result => writeFile(join(config.root, 'history-restart-result.json'), JSON.stringify(result, null, 2)),
      error => writeFile(join(config.root, 'history-restart-result.json'), JSON.stringify({ passed: false, error: error.message }))).catch(() => undefined);
  }, 1000);
  return () => clearTimeout(timer);
}
async function run(ctx, root) {
  const directory = join(root, 'home/skillhub/operations');
  const names = (await readdir(directory)).filter(name => name.endsWith('.json') && name !== 'host.json');
  assert.equal(names.length, 2, 'Prepare only the two installed-state fixture operations');
  const records = await Promise.all(names.map(async name => JSON.parse(await readFile(join(directory, name), 'utf8'))));
  const owner = records[0].ownerId;
  assert(records.every(record => record.ownerId === owner && record.operation.status.phase === 'completed'));
  const resolved = await ctx.sessionController.resolveAgent(owner);
  assert(!('error' in resolved), 'Original session must resolve after restart');
  assert.equal(ctx.skillhub.getCompatibility().localWritesAllowed, true);
  for (const record of records) {
    const operation = await ctx.skillhub.getInstallationOperation(owner, record.operationId);
    assert.equal(operation.operationId, record.operationId);
    assert.equal(operation.phase, 'completed');
  }
  const context = ctx.skillhub.getContext();
  const list = await ctx.typertGateway.invoke({ namespace: 'skillhub', method: 'listInstalled',
    args: { agentId: owner, sourceRevision: context.sourceRevision } });
  assert.equal(list.items.length, 2);
  assert.deepEqual(list.items.map(item => item.scope).sort(), ['project', 'user']);
  assert(list.items.every(item => item.state === 'present'));
  return { passed: true, originalSessionResolved: true, completedOperationsReadable: records.length,
    installedScopes: ['project', 'user'], bothPresent: true, modelInvoked: false, writesSubmitted: 0 };
}
