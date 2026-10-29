import { expect, test } from 'bun:test';
import { execFileSync } from 'node:child_process';
import { mkdtemp, mkdir, realpath, rm, symlink, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { MAX_PRESENTATION_BYTES, parseInstalledPresentation, readInstalledPresentations } from '../src/host/installed-presentation.js';
import type { InstalledProjectionItem } from '../src/host/generated/native-contract.js';

const bytes = (value: string) => new TextEncoder().encode(value);
const definition = '---\nname: local-helper\ndescription: |\n  整理工作记录\n  生成周报\n---\nPRIVATE INSTRUCTION BODY';
const presentation = { displayName: 'local-helper', summary: '整理工作记录 生成周报' };

test('frontmatter presents authored names and block descriptions without leaking body or other fields', () => {
  expect(parseInstalledPresentation(bytes(definition))).toEqual(presentation);
  expect(parseInstalledPresentation(bytes('---\nname: "quoted: name"\ndescription: "真实说明"\nsecret: private\n---\nprivate'))).toEqual({ displayName: 'quoted: name', summary: '真实说明' });
});
test('malformed, duplicate, tagged, aliased and non-string metadata fail closed', () => {
  for (const value of ['no frontmatter', '---\nname: [\n---', '---\nname: one\nname: two\n---', '---\nname: !custom one\n---', '---\nname: &name one\ndescription: *name\n---', '---\nname: [one]\ndescription: 12\n---']) expect(parseInstalledPresentation(bytes(value))).toEqual({});
});
test('prefix and fields are bounded, preserving valid headers when unicode bodies are cut mid-character', () => {
  expect(parseInstalledPresentation(new Uint8Array(MAX_PRESENTATION_BYTES + 1))).toEqual({});
  expect(parseInstalledPresentation(bytes('---\nname: '+ 'a'.repeat(MAX_PRESENTATION_BYTES)))).toEqual({});
  expect(parseInstalledPresentation(bytes('---\nname: '+ 'n'.repeat(200)+'\ndescription: '+ 'd'.repeat(2000)+'\n---'))).toEqual({ displayName: 'n'.repeat(128), summary: 'd'.repeat(1024) });
  expect(parseInstalledPresentation(bytes('# Body title\nNo metadata'))).toEqual({});
  for (let offset = 0; offset < 3; offset++) expect(parseInstalledPresentation(bytes(definition+' '.repeat(offset)+'中'.repeat(MAX_PRESENTATION_BYTES)).subarray(0, MAX_PRESENTATION_BYTES))).toEqual(presentation);
});
async function fixture(run: (root: string, item: InstalledProjectionItem, read: (items?: InstalledProjectionItem[], signal?: AbortSignal) => ReturnType<typeof readInstalledPresentations>) => Promise<void>) {
  const root = await realpath(await mkdtemp(join(tmpdir(), 'skillhub-presentation-')));
  const item: InstalledProjectionItem = { namespace: 'demo', slug: 'helper', version: '1', scope: 'user', installationState: 'enabled', state: 'present', targetPath: join(root, 'helper') };
  await mkdir(item.targetPath);
  try { await run(root, item, (items = [item], signal = new AbortController().signal) => readInstalledPresentations(items, [{ scope: 'user', root }], signal)); }
  finally { await rm(root, { recursive: true, force: true }); }
}
test('enabled and disabled installations read only their fixed entry with a bounded prefix', async () => fixture(async (_root, item, read) => {
  await writeFile(join(item.targetPath, 'SKILL.md'), definition+'中'.repeat(MAX_PRESENTATION_BYTES));
  await writeFile(join(item.targetPath, 'SKILL.md.disabled'), '---\nname: disabled-helper\ndescription: 停用时仍可展示\n---');
  expect(await read()).toEqual([presentation]);
  expect(await read([{ ...item, installationState: 'disabled' }])).toEqual([{ displayName: 'disabled-helper', summary: '停用时仍可展示' }]);
}));
test('missing, untrusted and invalid targets omit display fields without changing projection', async () => fixture(async (root, item, read) => {
  expect(await read()).toEqual([{}]); await writeFile(join(item.targetPath, 'SKILL.md'), definition);
  expect(await read([{ ...item, state: 'mismatch' }, { ...item, slug: '../helper' }, { ...item, targetPath: root }, { ...item, scope: 'project' }])).toEqual([{}, {}, {}, {}]);
  await expect(read([item], AbortSignal.abort())).rejects.toThrow();
}));
test.skipIf(process.platform === 'win32')('symlinked files and directories and FIFO entries cannot expose content or block', async () => fixture(async (root, item, read) => {
  const outside = join(root, 'private.md'); await writeFile(outside, definition);
  const entry = join(item.targetPath, 'SKILL.md'); await symlink(outside, entry);
  expect(await read()).toEqual([{}]); await rm(entry);
  execFileSync('mkfifo', [entry]); expect(await read()).toEqual([{}]); await rm(entry);
  await rm(item.targetPath, { recursive: true });
  const alternate = join(root, 'other'); await mkdir(alternate); await writeFile(join(alternate, 'SKILL.md'), definition);
  await symlink(alternate, item.targetPath); expect(await read()).toEqual([{}]);
  expect(await readInstalledPresentations([{ ...item, targetPath: join(item.targetPath, 'helper') }], [{ scope: 'user', root: item.targetPath }], new AbortController().signal)).toEqual([{}]);
}));
