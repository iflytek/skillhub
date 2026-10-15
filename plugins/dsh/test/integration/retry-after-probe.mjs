import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_FIXTURE_REQUEST_LOG: log } = process.env;
if (!entry || !output || !log) throw new Error('Set isolated Host URL, output and Registry request log');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage();
const errors = [];
let writes = 0;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => { if (/\/(startInstall|executePlan)$/.test(new URL(request.url()).pathname)) writes++; });
const calls = async () => (await readFile(log, 'utf8')).trim().split('\n').map(line => JSON.parse(line))
  .filter(item => item.path.endsWith('/search') && item.query === 'rate-limit').length;
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const welcome = page.getByRole('button', { name: /^(Continue|继续)$/ });
  const later = page.getByRole('button', { name: /^(Configure later|稍后配置)$/ });
  await welcome.or(later).first().waitFor();
  if (await welcome.isVisible()) await welcome.click();
  await later.click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: /document-helper/ }).waitFor();
  await plaza.getByLabel('搜索技能').fill('rate-limit');
  const first = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/listSkills'));
  await plaza.getByRole('button', { name: '搜索', exact: true }).click();
  const result = (await (await first).json()).result;
  assert.equal(result.ok, false); assert.equal(result.error.details.reason, 'RATE_LIMITED');
  assert(result.error.details.retryAfterSeconds > 0);
  await plaza.getByText(/请等待至少 \d+ 秒后手动重试/).waitFor();
  const retry = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/listSkills'));
  await plaza.getByRole('button', { name: '重试', exact: true }).click();
  const repeated = (await (await retry).json()).result;
  assert.equal(repeated.ok, false); assert.equal(repeated.error.details.reason, 'RATE_LIMITED');
  assert.equal(await calls(), 1);
  await plaza.getByText(/请等待至少 \d+ 秒后手动重试/).waitFor();
  await page.screenshot({ path: `${output}/retry-after.png` });
  await delay(8500);
  assert.equal(await calls(), 1);
  await plaza.getByRole('button', { name: '重试', exact: true }).click();
  await plaza.getByRole('button', { name: /document-helper/ }).waitFor();
  assert.equal(await calls(), 2);
  assert.equal(await plaza.getByLabel('搜索技能').inputValue(), 'rate-limit');
  assert.equal(writes, 0); assert.deepEqual(errors, []);
  await writeFile(`${output}/retry-after-result.json`, JSON.stringify({ passed: true, realRegistry429: true,
    waitSeconds: result.error.details.retryAfterSeconds, earlyRetryBlocked: true, noAutomaticRetry: true,
    manualRetryRecovered: true, queryRetained: true, registryRequests: 2, writes, errors }, null, 2));
} finally { await browser.close(); }
