import { expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import { FileSystemSkillProvider } from '@deepseek-ai/dsh-skill-filesystem';
import { realpath, mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { observeDshInstallation } from '../src/host/skill-discovery.js';

test('scoped discovery requires the exact directory, body and parsed invocation metadata', async () => {
  const directory = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-discovery-')));
  const ctx = new Context();
  const signal = new AbortController().signal;
  const provider = new FileSystemSkillProvider(ctx, { signal, invalidate: () => {} }, {
    providerName: 'fixture', includeDefaultRoots: false, customSkillDirs: [directory], watch: false,
  });
  try {
    await writeFile(join(directory, 'SKILL.md'), '---\nname: actual-name\ndescription: Write documents\n---\nRead references/note.md.');
    const listed = await provider.list({ signal });
    const candidates = Array.isArray(listed) ? listed : listed.candidates;
    const loaded = await provider.get(candidates[0]!, { signal });
    if (!loaded) throw new Error('Missing fixture');
    const lookup = async (name: string) => { expect(name).toBe('actual-name'); return loaded; };
    expect(await observeDshInstallation(ctx, [directory], signal, lookup)).toBe(true);
    expect(await observeDshInstallation(ctx, [], signal, lookup)).toBe(false);
    expect(await observeDshInstallation(ctx, [directory], signal, async () => undefined)).toBe(false);
    expect(await observeDshInstallation(ctx, [directory], signal,
      async () => ({ ...loaded, resourceBase: { kind: 'directory', path: join(directory, 'other') } }))).toBe(false);
    expect(await observeDshInstallation(ctx, [directory], signal,
      async () => ({ ...loaded, content: 'Stale cached body' }))).toBe(false);
    expect(await observeDshInstallation(ctx, [directory], signal,
      async () => ({ ...loaded, description: 'Other description' }))).toBe(false);
    expect(await observeDshInstallation(ctx, [directory], signal,
      async () => ({ ...loaded, metadata: { changed: true } }))).toBe(false);
    await expect(observeDshInstallation(ctx, [directory], AbortSignal.abort(), lookup)).rejects.toThrow();
    expect(ctx.get('skills')).toBeUndefined();
  } finally {
    await provider.dispose();
    await rm(directory, { recursive: true, force: true });
  }
});
