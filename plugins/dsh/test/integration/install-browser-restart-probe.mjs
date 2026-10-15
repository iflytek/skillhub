import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_SEARCH_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host, pending install fixture and output');
let browser;
const errors = [];
const operations = new Set();
let writes = 0;
async function open() {
  browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
  page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
  page.on('request', request => {
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/startInstall') || path.endsWith('/executePlan')) writes++;
    if (path.endsWith('/getOperation')) operations.add(request.postDataJSON().payload.args.operationId);
  });
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  assert.equal(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('skillhub:')).length), 0);
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  await page.getByText(title, { exact: true }).click();
  const card = page.getByRole('region', { name: 'SkillHub 安装结果', exact: true });
  if (!await card.isVisible()) {
    const process = page.locator('button[data-turn-process="1"]');
    if (await process.getAttribute('aria-expanded') === 'false') await process.click();
  }
  if (!await card.isVisible()) await page.getByText('Called tools', { exact: true }).click();
  return { page, card };
}
try {
  const first = await open();
  await first.card.getByText('正在安装，请稍候…', { exact: true }).waitFor();
  await first.page.screenshot({ path: `${output}/pending-before-browser-close.png` });
  await browser.close();
  const second = await open();
  await second.card.getByText('技能文件安装完成。', { exact: true }).waitFor({ timeout: 60000 });
  await second.card.getByText('当前项目 · 1.2.0 · 文件存在', { exact: true }).waitFor();
  assert.equal(writes, 0);
  assert.equal(operations.size, 1);
  assert.equal(typeof [...operations][0], 'string');
  assert.deepEqual(errors, []);
  await second.page.screenshot({ path: `${output}/completed-after-browser-restart.png` });
  const result = { passed: true, closedWhilePending: true, freshSessionStorage: true, browserRelaunched: true,
    originalOperationId: [...operations][0], writes, completedAfterReopen: true, errors };
  await writeFile(`${output}/browser-restart-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await browser?.close(); }
