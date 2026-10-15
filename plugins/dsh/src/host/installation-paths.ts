import type { FileSystem } from '@deepseek-ai/dsh-fs';
import { canonicalizeWatchPath, resolveDshHome } from '@deepseek-ai/dsh-home-paths';
import { writableRoots, type SandboxExecutionPolicy } from '@deepseek-ai/dsh-sandbox';
import { stat } from 'node:fs/promises';
import { dirname, isAbsolute, join, normalize, relative, sep } from 'node:path';
import type { ResolvedInstallationTarget } from './installation-targets.js';

type HostFileSystem = Pick<FileSystem, 'sandboxMode' | 'resolve' | 'processPath' | 'processPathFromHostPath'>;

/** Default local DSH skill roots. Custom provider roots need a separate verified adapter. */
export async function resolveInstallationPath(options: {
  scope: 'user' | 'project'; cwd: string; fs: HostFileSystem; signal: AbortSignal;
  policy?: SandboxExecutionPolicy;
}): Promise<ResolvedInstallationTarget> {
  if (options.fs.sandboxMode !== undefined && !options.policy) throw new Error('Sandboxed installation requires a policy-aware filesystem adapter');
  const target = await resolveInstalledPath(options);
  if (options.fs.sandboxMode !== undefined) await assertInstallationPolicy(target.root, options.policy!);
  options.signal.throwIfAborted();
  return target;
}

/** Read-only root resolution uses the same Host mapping without requiring write permission. */
export async function resolveInstalledPath(options: {
  scope: 'user' | 'project'; cwd: string; fs: HostFileSystem; signal: AbortSignal;
}): Promise<ResolvedInstallationTarget> {
  const { scope, fs, signal } = options;
  signal.throwIfAborted();
  if (!isAbsolute(options.cwd)) throw new Error('An absolute Host project context is required');
  const dshHome = await canonicalizeWatchPath(resolveDshHome());
  const cwd = await canonicalizeWatchPath(options.cwd);
  await assertHostMapping(fs, cwd, signal);
  if (!(await stat(cwd)).isDirectory()) throw new Error('Project context is not a directory');
  let project = cwd;
  if (scope === 'project') {
    for (let current = cwd; ; current = dirname(current)) {
      signal.throwIfAborted();
      try {
        await stat(join(current, '.git'));
        project = current;
        break;
      } catch (error) {
        if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error;
      }
      if (dirname(current) === current) break;
    }
  }
  const expected = scope === 'user' ? join(dshHome, 'skills') : join(project, '.dsh', 'skills');
  const root = await canonicalizeWatchPath(expected);
  if (root !== expected) throw new Error('Skill root redirects outside the resolved default root');
  await assertHostMapping(fs, root, signal);
  signal.throwIfAborted();
  return { scope, root, dshHome };
}

/** Preflight only. Every filesystem mutation must repeat this check under its target lock. */
export async function assertInstallationPolicy(path: string, policy: SandboxExecutionPolicy): Promise<void> {
  if (policy.mode === 'read-only') throw new Error('Installation is denied by the read-only session policy');
  if (policy.mode === 'danger-full-access') return;
  const canonical = await canonicalizeWatchPath(path);
  for (const allowed of writableRoots(policy)) {
    const root = await canonicalizeWatchPath(allowed);
    const offset = relative(root, canonical);
    if (!isAbsolute(offset) && offset !== '..' && !offset.startsWith(`..${sep}`)) return;
  }
  throw new Error('Installation target is outside the session writable roots');
}

/** All content, staging and backup paths must remain under the accepted canonical skill root. */
export async function assertInstallationMutation(root: string, paths: readonly string[], policy?: SandboxExecutionPolicy): Promise<void> {
  if (await canonicalizeWatchPath(root) !== root) throw new Error('Installation root changed');
  for (const path of paths) {
    const canonical = await canonicalizeWatchPath(path);
    const offset = relative(root, canonical);
    if (canonical !== path || isAbsolute(offset) || offset === '..' || offset.startsWith(`..${sep}`)) {
      throw new Error('Installation mutation escaped the accepted root');
    }
    if (policy) await assertInstallationPolicy(canonical, policy);
  }
}

async function assertHostMapping(fs: HostFileSystem, path: string, signal: AbortSignal): Promise<void> {
  const mapped = fs.processPathFromHostPath(path);
  if (!mapped || !isAbsolute(mapped) || normalize(mapped) !== path) throw new Error('Filesystem does not share the Host path');
  const target = await fs.resolve(mapped, { signal });
  if (normalize(fs.processPath(target)) !== path) throw new Error('Filesystem resolves to a different Host path');
}

/** Removal may reach exactly one journal-bound quarantine outside the skill discovery root. */
export async function assertRemovalMutation(root: string, slug: string, operationId: string,
  paths: readonly string[], policy?: SandboxExecutionPolicy): Promise<void> {
  if (!isAbsolute(root) || normalize(root) !== root || !/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(slug)
    || !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(operationId)) {
    throw new Error('Invalid removal target identity');
  }
  const trash = join(dirname(root), '.skillhub-trash');
  const allowed = new Set([root, join(root, slug), trash, join(trash, operationId)]);
  if (await canonicalizeWatchPath(root) !== root) throw new Error('Removal root changed');
  for (const path of paths) {
    if (!allowed.has(path) || await canonicalizeWatchPath(path) !== path) throw new Error('Removal mutation escaped its confirmed paths');
    if (policy) await assertInstallationPolicy(path, policy);
  }
}
