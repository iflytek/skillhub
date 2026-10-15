import { readFileSync, appendFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';

export function releasePolicy(pkg: { name: unknown; version: unknown; private?: unknown }, tag: string | undefined, publish: boolean) {
  const number = '(?:0|[1-9][0-9]*)';
  const versionPattern = new RegExp(`^${number}\\.${number}\\.${number}(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?$`);
  const match = typeof pkg.version === 'string' && versionPattern.exec(pkg.version);
  if (!match || match[0] !== pkg.version || match[1]?.split('.').some(part => /^0[0-9]+$/.test(part))) throw new Error('Invalid release version');
  if (tag !== `dsh-v${pkg.version}`) throw new Error('Tag must exactly match the plugin version');
  if (pkg.name !== '@astron-team/skillhub-dsh') throw new Error('Unexpected DSH package name');
  if (publish && pkg.private !== false) throw new Error('Release package must explicitly set private: false');
  return { version: pkg.version, package_name: pkg.name, dist_tag: match[1] ? 'next' : 'latest' };
}

if (process.argv[1] && pathToFileURL(resolve(process.argv[1])).href === import.meta.url) {
  const manifest = process.argv[2] ? resolve(process.argv[2]) : new URL('../package.json', import.meta.url);
  const pkg = JSON.parse(readFileSync(manifest, 'utf8'));
  if (process.env.GITHUB_REF_TYPE !== 'tag') throw new Error('Run this workflow on the release tag');
  const result = releasePolicy(pkg, process.env.RELEASE_TAG, process.env.PUBLISH === 'true');
  if (!process.env.GITHUB_OUTPUT) throw new Error('Missing workflow output file');
  appendFileSync(process.env.GITHUB_OUTPUT, Object.entries(result).map(([key, value]) => `${key}=${value}\n`).join(''));
}
