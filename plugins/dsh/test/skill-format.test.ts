import { expect, test } from 'bun:test';
import { Context } from '@deepseek-ai/cordis';
import { mkdtemp, mkdir, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { validateDshSkill } from '../src/host/skill-format.js';

test('official DSH provider validates a staged skill and its resources without registering it', async () => {
  const directory = await mkdtemp(join(tmpdir(), 'skillhub-format-'));
  try {
    await writeFile(join(directory, 'SKILL.md'), '---\nname: writer\ndescription: Write documents\nuser-invocable: true\n---\nRead references/note.md.');
    await mkdir(join(directory, 'references'));
    await writeFile(join(directory, 'references/note.md'), 'Resource');
    const ctx = new Context();
    await validateDshSkill(ctx, directory, new AbortController().signal);
    expect(ctx.get('skills')).toBeUndefined();
    await expect(validateDshSkill(ctx, directory, AbortSignal.abort())).rejects.toThrow();
  } finally { await rm(directory, { recursive: true, force: true }); }
});

test.each([
  '# No frontmatter',
  '---\nname: writer\n---\nMissing description',
  '---\nname: writer\ndescription: Write\nuser-invocable: invalid\n---\nBody',
  '---\nname: writer\ndescription: Write\nmodelInvocable: true\n---\nLegacy invocation',
])('official parser rejects incompatible staged SKILL.md: %s', async content => {
  const directory = await mkdtemp(join(tmpdir(), 'skillhub-format-invalid-'));
  try {
    await writeFile(join(directory, 'SKILL.md'), content);
    await expect(validateDshSkill(new Context(), directory, new AbortController().signal)).rejects.toThrow('not a valid DSH skill');
  } finally { await rm(directory, { recursive: true, force: true }); }
});
