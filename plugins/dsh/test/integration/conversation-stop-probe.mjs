import assert from 'node:assert/strict';
import { readFile, writeFile, access, mkdir } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium } from 'playwright';

// The caller creates a real empty Session, writes its marker, then waits for ready.json
// before submitting a real model prompt. This probe never submits prompts or invokes cancel RPCs.
const output = process.env.DSH_PROBE_OUTPUT;
const markerPath = process.env.DSH_STOP_SESSION_MARKER;
const fixtureLog = process.env.DSH_FIXTURE_REQUEST_LOG;
const mode = process.env.DSH_STOP_STAGE;
const skillFile = process.env.DSH_STOP_SKILL_FILE;
assert(output && markerPath && fixtureLog && skillFile && ['before', 'after'].includes(mode));
await mkdir(output, { recursive: true });
const marker = JSON.parse(await readFile(markerPath, 'utf8'));
assert(typeof marker.title === 'string' && typeof marker.project === 'string');
const exists = path => access(path).then(() => true, () => false);
assert(!await exists(skillFile), 'Use a fresh isolated install target');
const logOffset = (await readFile(fixtureLog, 'utf8').catch(() => '')).length;
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'en-US' });
const errors = [];
let cancelRequests = 0;
let browserInstallRequests = 0;
page.on('pageerror', () => errors.push('pageerror'));
page.on('request', request => {
  const path = new URL(request.url()).pathname;
  if (path.endsWith('/cancel')) cancelRequests++;
  if (path.endsWith('/startInstall')) browserInstallRequests++;
});
async function openSession() {
  const title = page.getByText(marker.title, { exact: true });
  if (!await title.waitFor({ timeout: 5000 }).then(() => true, () => false)) {
    await page.getByLabel('Sessions', { exact: true }).getByText(marker.project.split('/').at(-1), { exact: true }).click();
  }
  await title.click({ timeout: 120000 });
}
async function revealTools() {
  const processButton = page.locator('button[data-turn-process="1"]');
  if (await processButton.isVisible() && await processButton.getAttribute('aria-expanded') === 'false') await processButton.click();
  const called = page.getByText('Called tools', { exact: true });
  if (await called.isVisible()) await called.click();
}
try {
  for (const name of ['Continue', 'Configure later']) await page.addLocatorHandler(page.getByRole('button', { name, exact: true }), button => button.click());
  await page.goto(process.env.DSH_PROBE_URL, { waitUntil: 'domcontentloaded' });
  await writeFile(`${output}/ready.json`, JSON.stringify({ browserReady: true, mode }));
  await openSession();
  const stop = page.getByRole('button', { name: /^(Stop generating|停止生成)$/ }).first();
  const card = page.getByRole('region', { name: 'SkillHub 安装结果', exact: true });
  const deadline = Date.now() + 120000;
  let operationId;
  if (mode === 'before') {
    while (!(await readFile(fixtureLog, 'utf8').catch(() => '')).slice(logOffset).split('\n').some(line => {
      try { return JSON.parse(line).path?.endsWith('/resolve'); } catch { return false; }
    })) {
      assert(Date.now() < deadline, 'No real resolve request observed');
      await delay(50);
    }
    assert(!await exists(skillFile), 'Skill was written before Stop');
    assert(!await card.isVisible(), 'Installation already accepted; this is not the before case');
  } else {
    while (!await card.isVisible()) {
      assert(Date.now() < deadline, 'No real accepted installation card observed');
      await revealTools();
      await delay(100);
    }
    operationId = await card.locator('code').textContent();
    assert(operationId && !await exists(skillFile), 'Accepted case must still be awaiting file installation');
  }
  await stop.click({ timeout: 5000 });
  await writeFile(`${output}/clicked.json`, JSON.stringify({ clicked: true, mode, operationId }));
  await page.screenshot({ path: `${output}/stop-${mode}.png` });
  await stop.waitFor({ state: 'hidden', timeout: 15000 });
  if (mode === 'before') {
    // Exceed the fixture's maximum resolve delay, so a late response cannot mask a write.
    await delay(31000);
    assert(!await exists(skillFile), 'Stop before acceptance produced skill files');
  } else {
    if (!await card.isVisible()) await revealTools();
    await card.getByText('技能文件安装完成。', { exact: true }).waitFor({ timeout: 45000 });
    assert(await exists(skillFile), 'Accepted operation did not finish writing');
    await card.getByRole('button', { name: '查询原操作', exact: true }).click();
    await page.reload({ waitUntil: 'domcontentloaded' });
    await openSession();
    await revealTools();
    await card.waitFor();
    assert.equal(await card.locator('code').textContent(), operationId, 'History switched to another operation');
    await card.getByRole('button', { name: '查询原操作', exact: true }).click();
    await card.getByText('技能文件安装完成。', { exact: true }).waitFor();
    await page.screenshot({ path: `${output}/stop-after-history.png` });
  }
  assert.equal(cancelRequests, 1, 'Expected one cancellation from the actual UI button');
  assert.equal(browserInstallRequests, 0);
  assert.deepEqual(errors, []);
  await writeFile(`${output}/result.json`, JSON.stringify({ passed: true, mode, actualStopButtonClicked: true,
    cancelRequests, browserInstallRequests, pageErrors: errors, skillFilePresent: await exists(skillFile),
    sameOperationAfterReload: mode === 'after' ? true : undefined,
    limitation: 'Observer must separately prove genuine model tool calls, acceptance ordering, operation count and complete target inventory.' }, null, 2));
} catch (error) {
  await page.screenshot({ path: `${output}/failure.png` }).catch(() => {});
  await writeFile(`${output}/failure-ui.txt`, await page.locator('body').innerText().catch(() => '')).catch(() => {});
  throw error;
} finally {
  await browser.close();
}
