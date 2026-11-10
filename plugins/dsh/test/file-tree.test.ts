import { expect, test } from 'bun:test';
import type { SkillFileEntry } from '../src/host/types.js';
import { buildSkillFileTree, fileAncestors, fileBrowserStateKey, fileLineNumbers, revealFileDirectories, type SkillFileNode } from '../src/client/file-tree.js';

const file = (path: string): SkillFileEntry => ({ path, size: 10, contentType: 'text/plain' });
function paths(nodes: SkillFileNode[]): string[] {
  return nodes.flatMap(node => node.kind === 'file' ? [node.path] : paths(node.children));
}

test('file tree preserves complete paths, same basenames, magic names and file-directory prefix collisions', () => {
  const entries = ['a/notes.md', 'b/notes.md', '__proto__/constructor/value.txt', 'a', 'constructor', 'SKILL.md'].map(file);
  const before = JSON.stringify(entries);
  for (const entry of entries) Object.freeze(entry);
  Object.freeze(entries);
  const tree = buildSkillFileTree(entries);
  expect(paths(tree.children).sort()).toEqual(entries.map(entry => entry.path).sort());
  expect(tree.fileCount).toBe(6); expect(tree.directoryCount).toBe(4);
  expect(tree.children[0]).toMatchObject({ kind: 'file', path: 'SKILL.md' });
  expect(tree.children.filter(node => node.path === 'a').map(node => node.kind).sort()).toEqual(['directory', 'file']);
  expect(JSON.stringify(entries)).toBe(before);
});

test('tree order is deterministic and counts come from a nested manifest, not hardcoded folder names', () => {
  const entries = Array.from({ length: 200 }, (_, index) => file(`area-${index % 5}/group-${index % 7}/note-${index}.md`));
  const tree = buildSkillFileTree(entries);
  expect(tree.fileCount).toBe(200); expect(tree.directoryCount).toBe(40);
  expect(paths(tree.children)).toEqual(paths(buildSkillFileTree([...entries].reverse()).children));
  expect(new Set(paths(tree.children)).size).toBe(200);
});

test('revealing a selected file opens only its ancestors without mutating user directory state', () => {
  const folded = new Set(['', 'other']);
  const revealed = revealFileDirectories(folded, 'custom/deep/note.md');
  expect([...revealed]).toEqual(['', 'other', 'custom', 'custom/deep']);
  expect([...folded]).toEqual(['', 'other']);
  expect(revealFileDirectories(revealed, 'custom/deep/note.md')).toBe(revealed);
  expect(fileAncestors('SKILL.md')).toEqual([]);
  expect(fileAncestors('a/b/notes.md')).toEqual(['a', 'a/b']);
});

test('line numbering handles empty text, CRLF, CR, LF and a trailing empty line', () => {
  expect(fileLineNumbers('')).toBe('1');
  expect(fileLineNumbers('one')).toBe('1');
  expect(fileLineNumbers('one\r\ntwo\rthree\n')).toBe('1\n2\n3\n4');
  expect(fileLineNumbers('\n\n')).toBe('1\n2\n3');
});

test('a maximum-byte newline-only preview remains one gutter string rather than per-line nodes', () => {
  const numbers = fileLineNumbers('\n'.repeat(256 * 1024));
  expect(numbers.startsWith('1\n2\n3\n')).toBe(true);
  expect(numbers.endsWith('\n262145')).toBe(true);
});


test('file browser state keys isolate fixed-version detail tokens and source identities', () => {
  expect(fileBrowserStateKey('source-A/version-1', 'detail-1')).toEqual(['skillhub-file-ui', 'source-A/version-1', 'detail-1']);
  expect(fileBrowserStateKey('source-A/version-1', 'detail-1')).not.toEqual(fileBrowserStateKey('source-B/version-1', 'detail-1'));
  expect(fileBrowserStateKey('source-A/version-1', 'detail-1')).not.toEqual(fileBrowserStateKey('source-A/version-1', 'detail-2'));
});
