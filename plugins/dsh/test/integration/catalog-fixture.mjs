import { createServer } from 'node:http';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { appendFileSync, readFileSync } from 'node:fs';
const { zipSync } = createRequire(new URL('../../../../cli/package.json', import.meta.url))('fflate');
const files = { 'SKILL.md': '---\nname: document-helper\ndescription: Isolated native installation fixture.\n---\nRead references/note.md.', 'references/note.md': 'Native installation resource' };
const aggregate = createHash('sha256');
for (const [name, content] of Object.entries(files).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)) aggregate.update(`${name}:${createHash('sha256').update(content).digest('hex')}\n`);
const fingerprint = 'sha256:' + aggregate.digest('hex');
const archive = zipSync(Object.fromEntries(Object.entries(files).map(([name, content]) => [name, new TextEncoder().encode(content)])));
const upgradedFiles = { ...files, 'references/note.md': 'Native upgraded resource 1.3.0' };
const upgradedAggregate = createHash('sha256');
for (const [name, content] of Object.entries(upgradedFiles).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)) upgradedAggregate.update(`${name}:${createHash('sha256').update(content).digest('hex')}\n`);
const upgradedFingerprint = 'sha256:' + upgradedAggregate.digest('hex');
const upgradedArchive = zipSync(Object.fromEntries(Object.entries(upgradedFiles).map(([name, content]) => [name, new TextEncoder().encode(content)])));
const fixturePort = Number(process.env.DSH_FIXTURE_PORT ?? 43219);
if (!Number.isInteger(fixturePort) || fixturePort < 1024 || fixturePort > 65535) throw new Error('Invalid fixture port');
const item = {namespace:process.env.DSH_FIXTURE_NAMESPACE ?? 'demo',slug:'document-helper',latestVersion:'1.2.0',summary:'整理文档并提取关键结论。'};
let rateLimited = false;
createServer((req,res)=>{
 const url = new URL(req.url,'http://127.0.0.1');
 if (process.env.DSH_FIXTURE_REQUEST_LOG) appendFileSync(process.env.DSH_FIXTURE_REQUEST_LOG,
  JSON.stringify({ path: url.pathname, query: url.searchParams.get('q'), version: url.searchParams.get('version'), page: url.searchParams.get('page'), size: url.searchParams.get('size') }) + '\n');
 const namespace = /\/skills\/([^/]+)\/document-helper(?:\/|$)/.exec(url.pathname)?.[1] ?? item.namespace;
 res.setHeader('Content-Type','application/json');
 if (process.env.DSH_FIXTURE_RETRY_AFTER === '1' && url.pathname.endsWith('/search')
  && url.searchParams.get('q') === 'rate-limit' && !rateLimited) {
  rateLimited = true; res.statusCode = 429; res.setHeader('Retry-After', '8'); res.end('{}'); return;
 }
 if (url.pathname.endsWith('/search')) { const items=url.searchParams.get('q')==='nothing'?[]:process.env.DSH_FIXTURE_CATALOG_SCROLL === '1'
  ? Array.from({ length: 20 }, (_, index) => ({ ...item, namespace: `demo-${index}` })) : [item];
  const total = items.length && process.env.DSH_FIXTURE_CATALOG_TOTAL ? Number(process.env.DSH_FIXTURE_CATALOG_TOTAL) : items.length;
  if (!Number.isSafeInteger(total) || total < items.length) { res.statusCode=500;res.end('{}');return; }
  res.end(JSON.stringify({data:{items,total,limit:20}})); }
 else if (url.pathname === `/api/v1/skills/${namespace}/document-helper`) res.end(JSON.stringify({data:{id:42,namespace,slug:item.slug,displayName:'Document helper',summary:item.summary}}));
 else if (url.pathname.endsWith('/versions') || url.pathname.endsWith('/reviews')) {
  const moduleStatus = Number(process.env.DSH_FIXTURE_MODULE_STATUS ?? 200);
  if ([403, 404, 503].includes(moduleStatus)) { res.statusCode=moduleStatus;res.end('{}');return; }
  if (url.pathname.endsWith('/reviews') && process.env.DSH_FIXTURE_MODULE_STATE
   && readFileSync(process.env.DSH_FIXTURE_MODULE_STATE, 'utf8').trim() === 'unavailable') {
   res.statusCode=503;res.end('{}');return;
  }
  const page=Number(url.searchParams.get('page')); const size=Number(url.searchParams.get('size'));
  if (process.env.DSH_FIXTURE_EMPTY_MODULES === '1') {
   res.end(JSON.stringify({data:{items:[],total:0,page,size}}));return;
  }
  const entries=url.pathname.endsWith('/versions') ? [{version:'1.2.0',changelog:'改进文档提取',publishedAt:'2026-10-13T00:00:00Z',downloadAvailable:true}] : [{id:8,displayName:'示例用户',score:5,reviewText:'适合整理长文档',createdAt:'2026-10-13T00:00:00Z'}];
  if (process.env.DSH_FIXTURE_PAGINATION === '1') {
   for (let index = 1; index < 21; index++) entries.push(url.pathname.endsWith('/versions')
    ? { version: `0.0.${index}`, changelog: `历史版本 ${index}`, publishedAt: null, downloadAvailable: true }
    : { id: 100 + index, displayName: `用户 ${index}`, score: 4, reviewText: `评论内容 ${index}`, createdAt: '2026-10-13T00:00:00Z' });
  }
  res.end(JSON.stringify({data:{items:entries.slice(page * size, (page + 1) * size),total:entries.length,page,size}}));
 }
 else if (url.pathname.endsWith('/resolve')) {
  const scenario = url.searchParams.get('version') === '1.3.0' ? 'newer' : process.env.DSH_FIXTURE_UPDATE_STATE && !url.searchParams.has('version')
   ? readFileSync(process.env.DSH_FIXTURE_UPDATE_STATE, 'utf8').trim() : 'current';
  const candidates = { current: ['1.2.0', fingerprint], newer: ['1.3.0', upgradedFingerprint], older: ['1.1.0', fingerprint],
   changed: ['1.2.0', 'sha256:' + 'a'.repeat(64)], unknown: ['next-release', fingerprint] };
  if (!Object.hasOwn(candidates, scenario)) { res.statusCode=503; res.end('{}'); return; }
  let [version, candidateFingerprint] = candidates[scenario];
  const candidateState = process.env.DSH_FIXTURE_DOWNLOAD_STATE && url.searchParams.get('version') === '1.3.0'
   ? readFileSync(process.env.DSH_FIXTURE_DOWNLOAD_STATE, 'utf8').trim() : 'normal';
  if (candidateState === 'resolve-fingerprint') candidateFingerprint = 'sha256:' + 'b'.repeat(64);
  if (candidateState === 'resolve-version') version = '1.4.0';
  const delay = Number(process.env.DSH_FIXTURE_RESOLVE_DELAY_MS ?? 0);
  if (!Number.isInteger(delay) || delay < 0 || delay > 30000) {
   res.statusCode = 500; res.end('Invalid fixture resolve delay'); return;
  }
  const send = () => res.end(JSON.stringify({data:{namespace,slug:item.slug,version,versionId:version==='1.3.0'?2:1,fingerprint:candidateFingerprint,downloadUrl:`/download?version=${version}`}}));
  if (delay) setTimeout(send, delay); else send();
 }
 else if (url.pathname === '/download') {
  if (process.env.DSH_FIXTURE_DOWNLOAD_FAILURE === '1') { res.statusCode=503;res.end('{}');return; }
  const downloadState = process.env.DSH_FIXTURE_DOWNLOAD_STATE && url.searchParams.get('version') === '1.3.0'
   ? readFileSync(process.env.DSH_FIXTURE_DOWNLOAD_STATE, 'utf8').trim() : 'normal';
  if (downloadState === 'missing') { res.statusCode=404;res.end('{}');return; }
  if (downloadState === 'tampered') { res.setHeader('Content-Type','application/zip');res.end(archive);return; }
  if (downloadState !== 'normal') { res.statusCode=500;res.end('{}');return; }

  const delay = Number(process.env.DSH_FIXTURE_DOWNLOAD_DELAY_MS ?? 0);
  if (!Number.isInteger(delay) || delay < 0 || delay > 30000) {
   res.statusCode = 500; res.end('Invalid fixture download delay'); return;
  }
  const send = () => { res.setHeader('Content-Type', 'application/zip'); res.end(url.searchParams.get('version') === '1.3.0' ? upgradedArchive : archive); };
  if (delay) setTimeout(send, delay); else send();
 }
 else if (url.pathname.endsWith('/file')) {res.setHeader('Content-Type','text/plain'); res.end('# 文档助手\n\n提取摘要 &amp; 关键结论。\n\n<script>window.fixtureUnsafe=true</script>\n\n[危险链接](javascript:alert(1))\n\n![远程图片](https://example.invalid/tracker.png)');}
 else {res.statusCode=404;res.end('{}');}
}).listen(fixturePort,'127.0.0.1');
