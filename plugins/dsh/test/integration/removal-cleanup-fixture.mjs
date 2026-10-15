import assert from 'node:assert/strict';
import { chmod, stat, writeFile } from 'node:fs/promises';
import { resolve, sep, join } from 'node:path';
import { tmpdir } from 'node:os';

export const inject = ['skillhub'];
export function apply(ctx, config) {
  const root = resolve(config.root);
  assert(root.startsWith(resolve(tmpdir()) + sep));
  assert.equal(process.env.DSH_HOME, join(root, 'home'));
  assert(process.getuid && process.getuid() !== 0, 'Requires non-root POSIX permissions');
  let original;
  let changed;
  const timer = setTimeout(async () => {
    try {
      const runtime = ctx.skillhub.localRuntime;
      assert(runtime);
      original = runtime.submitRemoval;
      runtime.submitRemoval = (request, prepare) => original.call(runtime, request, async () => {
        const prepared = await prepare();
        const authorize = prepared.execution.authorize;
        return { ...prepared, execution: { ...prepared.execution, authorize: async (paths, phase, operationId) => {
          await authorize(paths, phase, operationId);
          if (phase !== 'cleanup' || changed) return;
          assert.equal(paths.length, 1);
          const path = paths[0];
          assert(path.startsWith(root + sep) && path.endsWith(`${sep}.skillhub-trash${sep}${operationId}`));
          const mode = (await stat(path)).mode & 0o777;
          changed = { path, mode, operationId };
          await chmod(path, 0o500);
          await writeFile(join(root, 'cleanup-injected.json'), JSON.stringify(changed));
        } } };
      });
      await writeFile(join(root, 'cleanup-fixture-ready.json'), JSON.stringify({ ready: true }));
    } catch (error) {
      await writeFile(join(root, 'cleanup-fixture-ready.json'), JSON.stringify({ error: error.message }));
    }
  }, 500);
  return async () => {
    clearTimeout(timer);
    if (original) ctx.skillhub.localRuntime.submitRemoval = original;
    if (changed) await chmod(changed.path, changed.mode).catch(error => { if (error.code !== 'ENOENT') throw error; });
  };
}
