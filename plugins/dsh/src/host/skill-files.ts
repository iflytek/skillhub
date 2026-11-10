import type { SkillFileEntry } from './types.js';

export const MAX_SKILL_PREVIEW_FILES = 500;
export const MAX_SKILL_PREVIEW_BYTES = 256 * 1024;

/** Registry-relative packaged names only; never a Host filesystem path or URL. */
export function isRegistryFilePath(path: string): boolean {
  // eslint-disable-next-line no-control-regex -- Reject untrusted control characters in file references.
  return path.length > 0 && path.length <= 2048 && !/[\u0000-\u001f\u007f\\]/u.test(path)
    && !path.startsWith('/') && !/^[a-z]:/iu.test(path)
    && path.split('/').every(part => part !== '' && part !== '.' && part !== '..');
}

export function isPreviewableText(file: SkillFileEntry): boolean {
  const mime = file.contentType?.split(';', 1)[0]?.trim().toLowerCase() ?? '';
  if (mime.startsWith('text/') || /(?:json|javascript|xml|yaml|toml)$/u.test(mime)) return true;
  const name = file.path.split('/').at(-1)!.toLowerCase();
  return /\.(?:md|mdx|txt|json|jsonc|yaml|yml|toml|ini|cfg|conf|csv|tsv|xml|svg|html|css|scss|js|jsx|mjs|cjs|ts|tsx|py|sh|bash|ps1|java|kt|rs|go|sql|rb|c|h|cpp|hpp)$/u.test(name)
    || /^(?:dockerfile|makefile|license|notice|\.gitignore|\.editorconfig)$/u.test(name);
}
