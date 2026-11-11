import type { SkillFileEntry } from '../host/types.js';

export interface SkillFileBrowserState { selected: string | null; expanded: readonly string[] }
export const fileBrowserStateKey = (identity: string, token: string) => ['skillhub-file-ui', identity, token] as const;

export type SkillFileNode = SkillFileDirectory | SkillFileLeaf;
export interface SkillFileDirectory { kind: 'directory'; path: string; name: string; children: SkillFileNode[] }
export interface SkillFileLeaf { kind: 'file'; path: string; name: string; file: SkillFileEntry }

/** Build presentation nodes without changing the Registry's fixed-version manifest. */
export function buildSkillFileTree(files: readonly SkillFileEntry[]) {
  const root: SkillFileDirectory = { kind: 'directory', path: '', name: '', children: [] };
  const directories = new Map<string, SkillFileDirectory>([['', root]]);
  for (const file of files) {
    const parts = file.path.split('/');
    let parent = root;
    let path = '';
    for (let index = 0; index < parts.length - 1; index++) {
      path = path ? `${path}/${parts[index]}` : parts[index]!;
      let directory = directories.get(path);
      if (!directory) {
        directory = { kind: 'directory', path, name: parts[index]!, children: [] };
        directories.set(path, directory); parent.children.push(directory);
      }
      parent = directory;
    }
    parent.children.push({ kind: 'file', path: file.path, name: parts.at(-1)!, file });
  }
  for (const directory of directories.values()) directory.children.sort((a, b) => {
    const aEntry = a.kind === 'file' && a.path === 'SKILL.md';
    const bEntry = b.kind === 'file' && b.path === 'SKILL.md';
    if (aEntry !== bEntry) return aEntry ? -1 : 1;
    if (a.kind !== b.kind) return a.kind === 'directory' ? -1 : 1;
    return a.name < b.name ? -1 : a.name > b.name ? 1 : 0;
  });
  return { children: root.children, fileCount: files.length, directoryCount: directories.size - 1 };
}

export function fileAncestors(path: string): string[] {
  const parts = path.split('/');
  return parts.slice(0, -1).map((_, index) => parts.slice(0, index + 1).join('/'));
}

/** A new selection reveals its ancestry; unrelated renders keep the user's folded state. */
export function revealFileDirectories(expanded: ReadonlySet<string>, path: string): ReadonlySet<string> {
  const required = ['', ...fileAncestors(path)];
  if (required.every(directory => expanded.has(directory))) return expanded;
  return new Set([...expanded, ...required]);
}

/** A single gutter text node, including an empty final line, avoids one DOM node per line. */
export function fileLineNumbers(content: string): string {
  let lines = 1;
  for (let index = 0; index < content.length; index++) {
    if (content[index] === '\n') lines++;
    else if (content[index] === '\r') { lines++; if (content[index + 1] === '\n') index++; }
  }
  return Array.from({ length: lines }, (_, index) => index + 1).join('\n');
}
