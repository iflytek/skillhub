import type { Context } from '@deepseek-ai/cordis';
import { FileSystemSkillProvider } from '@deepseek-ai/dsh-skill-filesystem';
import { join } from 'node:path';

/** Use the pinned DSH parser without registering a provider or watching staging directories. */
export async function validateDshSkill(ctx: Context, directory: string, signal: AbortSignal): Promise<void> {
  signal.throwIfAborted();
  const provider = new FileSystemSkillProvider(ctx, { signal, invalidate: () => {} }, {
    providerName: 'skillhub-install-validation', includeDefaultRoots: false, customSkillDirs: [directory], watch: false,
  });
  try {
    const result = await provider.list({ signal });
    signal.throwIfAborted();
    if (!Array.isArray(result) && !result.complete) throw new Error('DSH skill validation could not complete');
    const candidates = Array.isArray(result) ? result : result.candidates;
    const candidate = candidates.find(item => item.path === join(directory, 'SKILL.md'));
    if (!candidate) throw new Error('Package SKILL.md is not a valid DSH skill');
    const skill = await provider.get(candidate, { signal });
    if (!skill || skill.resourceBase?.kind !== 'directory' || skill.resourceBase.path !== directory) {
      throw new Error('DSH skill resources do not resolve to the package directory');
    }
  } finally { await provider.dispose(); }
}
