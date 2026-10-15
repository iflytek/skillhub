import type { Context } from '@deepseek-ai/cordis';
import { FileSystemSkillProvider } from '@deepseek-ai/dsh-skill-filesystem';
import { join } from 'node:path';
import { isDeepStrictEqual } from 'node:util';

type LoadedSkill = NonNullable<Awaited<ReturnType<FileSystemSkillProvider['get']>>>;

/** Compare the scoped registry winner with the exact installed package, without registering another provider. */
export async function observeDshInstallation(ctx: Context, paths: readonly string[], signal: AbortSignal,
  lookup: (name: string) => Promise<LoadedSkill | undefined>): Promise<boolean> {
  for (const directory of paths) {
    signal.throwIfAborted();
    const provider = new FileSystemSkillProvider(ctx, { signal, invalidate: () => {} }, {
      providerName: 'skillhub-discovery-check', includeDefaultRoots: false, customSkillDirs: [directory], watch: false,
    });
    try {
      const listed = await provider.list({ signal });
      if (!Array.isArray(listed) && !listed.complete) return false;
      const candidates = Array.isArray(listed) ? listed : listed.candidates;
      const candidate = candidates.find(item => item.path === join(directory, 'SKILL.md'));
      if (!candidate) return false;
      const expected = await provider.get(candidate, { signal });
      if (!expected) return false;
      const actual = await lookup(expected.name);
      signal.throwIfAborted();
      if (!actual || actual.resourceBase?.kind !== 'directory' || actual.resourceBase.path !== directory
        || actual.path !== expected.path || actual.name !== expected.name || actual.content !== expected.content
        || actual.description !== expected.description || actual.whenToUse !== expected.whenToUse
        || !isDeepStrictEqual(actual.invocation, expected.invocation)
        || !isDeepStrictEqual(actual.metadata, expected.metadata)) return false;
    } finally { await provider.dispose(); }
  }
  return paths.length > 0;
}
