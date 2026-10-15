// Isolated Host probe only; never include in the published bundle.
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { isAbsolute, join, relative, resolve } from 'node:path';
import { setTimeout as delay } from 'node:timers/promises';

export const inject = ['skillhub', 'sessions'];
export function apply(ctx, config) {
  const root = resolve(config.root);
  const rel = relative(resolve(tmpdir()), root);
  assert(rel && !rel.startsWith('..') && !isAbsolute(rel));
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  const abort = new AbortController();
  async function run() {
    const deadline = Date.now() + 10_000;
    while (Date.now() < deadline) {
      const status = ctx.skillhub.getLocalRuntimeStatus();
      if (status?.state === 'failed') throw new Error('Local runtime initialization failed');
      if (status?.state === 'ready') {
        const saved = JSON.parse(await readFile(join(root, 'home/skillhub/operations/host.json'), 'utf8'));
        assert.equal(status.hostInstanceId, saved.hostInstanceId);
        assert.equal(status.pendingOperations, 0);
        return { passed: true, hostInstanceId: status.hostInstanceId, pendingOperations: status.pendingOperations };
      }
      await delay(20, undefined, { signal: abort.signal });
    }
    throw new Error('Local runtime did not initialize');
  }
  void run().then(result => writeFile(join(root, 'local-runtime-report.json'), JSON.stringify(result)),
    error => writeFile(join(root, 'local-runtime-report.json'), JSON.stringify({ passed: false, reason: error.message }))).catch(() => undefined);
  return () => abort.abort();
}
