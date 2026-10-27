#!/usr/bin/env node
// The pinned upstream crawler exits zero for failed or empty crawls. Fail closed
// on its real result rather than treating repository discovery as documentation.
const { spawnSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const [crawlerDirectory, repository] = process.argv.slice(2);
if (!crawlerDirectory || !/^[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+$/.test(repository ?? '')) {
  console.error('DeepWiki requires a crawler directory and owner/repository.');
  process.exit(1);
}
const cwd = path.resolve(crawlerDirectory);
const deadline = Date.now() + 9 * 60 * 1000;

function run(name) {
  const result = spawnSync(process.execPath, [path.join(cwd, 'scripts', name), repository], {
    cwd,
    encoding: 'utf8',
    maxBuffer: 4 * 1024 * 1024,
    timeout: Math.max(1, deadline - Date.now()),
  });
  if (result.stdout) process.stdout.write(result.stdout);
  if (result.stderr) process.stderr.write(result.stderr);
  if (result.error || result.status !== 0) {
    console.error(`DeepWiki ${name} failed or exceeded its execution limit.`);
    process.exit(result.status > 0 && result.status <= 255 ? result.status : 1);
  }
  return result.stdout;
}

run('check-deepwiki.js');
const content = path.join(cwd, 'deepwiki-content.md');
const previousContent = fs.lstatSync(content, { throwIfNoEntry: false });
if (previousContent && !previousContent.isFile()) {
  console.error('DeepWiki crawl refused: output path must not be a symlink or other non-regular file.');
  process.exit(1);
}
// Remove only this generated output so a stale file cannot validate a new crawl.
if (previousContent) fs.unlinkSync(content);
const output = run('crawl-deepwiki.js');
const summaries = output.split(/\r?\n/).filter(line => line.startsWith('Crawl complete.'));
const match = summaries.length === 1 && summaries[0].match(
  /^Crawl complete\. Attempted: (\d+), Successful: (\d+), Failed: (\d+)$/,
);
const counts = match ? match.slice(1).map(Number) : [];
if (!match || !counts.every(Number.isSafeInteger) || counts[0] < 1 || counts[1] < 1
    || counts[2] !== 0 || counts[0] !== counts[1] + counts[2]) {
  console.error('DeepWiki crawl failed: missing, inconsistent, empty or unsuccessful page summary.');
  process.exit(1);
}
if (!fs.existsSync(content) || !fs.lstatSync(content).isFile()
    || !fs.readFileSync(content, 'utf8').trim()) {
  console.error('DeepWiki crawl failed: no extracted documentation content.');
  process.exit(1);
}
console.log(`DeepWiki verified: ${counts[1]} pages fetched, non-empty documentation content, no failed pages.`);
