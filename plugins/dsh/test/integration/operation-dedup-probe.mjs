import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host, output and two-card fixture title');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1800 } });
let requests = 0; let active = 0; let maximum = 0; let writes = 0;
const identities = new Set(); const errors = [];
page.on('pageerror', error => errors.push(error.message));
page.on('request', request => {
  if (/\/(startInstall|executePlan|recoverOperation)$/.test(new URL(request.url()).pathname)) writes++;
});
await page.route('**/getOperation', async route => {
  requests++; active++; maximum = Math.max(maximum, active);
  try {
    const response = await route.fetch();
    const body = await response.json();
    assert.equal(body.result?.ok, true);
    identities.add(body.result.value.operationId);
    if (requests <= 2) { body.result.value.phase = 'staging'; body.result.value.files = 'pending'; }
    await delay(1500);
    await route.fulfill({ response, json: body });
  } finally { active--; }
});
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: /^(Configure later|稍后配置)$/ }).click();
  await page.getByText(title, { exact: true }).click();
  const cards = page.getByRole('region', { name: 'SkillHub 安装结果', exact: true });
  const turns = page.locator('button[data-turn-process]:enabled');
  for (let i = 0; i < await turns.count(); i++) {
    if (await turns.nth(i).getAttribute('aria-expanded') === 'false') await turns.nth(i).click();
  }
  const groups = page.getByText(/^(Called tools|调用的工具|已调用工具)$/);
  if (await cards.count() < 2) {
    for (let i = 0; i < await groups.count(); i++) await groups.nth(i).click();
  }
  await cards.nth(1).waitFor();
  assert.equal(await cards.count(), 2);
  await cards.nth(0).getByText('正在安装，请稍候…', { exact: true }).waitFor();
  await cards.nth(1).getByText('正在安装，请稍候…', { exact: true }).waitFor();
  for (let i = 0; i < 2; i++) await cards.nth(i).getByText('技能文件安装完成。', { exact: true }).waitFor();
  assert.equal(maximum, 1, 'Two consumers must share one in-flight operation request');
  assert.equal(identities.size, 1); assert.equal(writes, 0); assert.deepEqual(errors, []);
  const settledRequests = requests;
  await page.waitForTimeout(2500);
  assert.equal(requests, settledRequests, 'Completed operation must stop polling');
  await page.screenshot({ path: `${output}/operation-dedup.png` });
  const result = { passed: true, visibleConsumers: 2, requests, maximumInFlight: maximum, sharedOperation: true,
    bothObservedPendingAndCompleted: true, terminalPollingStopped: true, browserWrites: writes, pendingProjectionInjected: true, modelInvoked: false };
  await writeFile(`${output}/operation-dedup-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/operation-dedup-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message, requests, maximum, writes })); process.exitCode = 1;
} finally { await browser.close(); }
