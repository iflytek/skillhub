import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title, DSH_PHASE_CONTROL_DIR: control } = process.env;
if (!entry || !output || !title || !control) throw new Error('Set isolated Host, output, title and phase control directory');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1100 } });
const errors = []; let writes = 0; let stage = 'open';
page.on('pageerror', error => errors.push(error.message));
page.on('request', request => { if (/\/(startInstall|executePlan|recoverOperation)$/.test(new URL(request.url()).pathname)) writes++; });
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.addLocatorHandler(page.getByRole('button', { name: /^(Configure later|稍后配置)$/ }), async locator => { await locator.click(); });
  await page.getByText(title, { exact: true }).click();
  stage = 'preparing';
  await page.getByText('正在准备 SkillHub 搜索…', { exact: true }).waitFor();
  await page.getByText('正在准备 SkillHub 安装请求…', { exact: true }).waitFor();
  await page.screenshot({ path: `${output}/tool-phases-preparing.png` });
  await writeFile(join(control, 'phase-command'), 'start');
  stage = 'start';
  await page.getByText('正在搜索 SkillHub…', { exact: true }).waitFor();
  await page.getByText('正在核验技能与安装位置…', { exact: true }).waitFor();
  await page.screenshot({ path: `${output}/tool-phases-start.png` });
  await writeFile(join(control, 'phase-command'), 'result');
  stage = 'result';
  const resultCard = page.getByRole('region', { name: 'SkillHub 搜索结果', exact: true });
  const turn = page.locator('button[data-turn-process]:enabled').last();
  await resultCard.or(turn).first().waitFor();
  if (!await resultCard.isVisible() && await turn.getAttribute('aria-expanded') === 'false') await turn.click();
  if (!await resultCard.isVisible()) {
    const group = page.getByText(/^(Called tools|调用的工具|已调用工具)$/);
    await group.waitFor(); await group.click();
  }
  await page.getByRole('region', { name: 'SkillHub 搜索结果', exact: true }).waitFor();
  await page.getByRole('region', { name: 'SkillHub 安装请求未确认', exact: true }).waitFor();
  assert.equal(writes, 0); assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/tool-phases-result.png` });
  const result = { passed: true, syntheticOfficialEvents: true, preparing: true, start: true, result: true,
    presentationMetadataWithoutBody: true, browserWrites: writes, modelInvoked: false, installationExecuted: false };
  await writeFile(`${output}/tool-phases-result.json`, JSON.stringify(result, null, 2)); console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/tool-phases-failure.png` });
  console.log(JSON.stringify({ passed: false, stage, error: error.message.split('Call log:')[0], writes, pageErrorCount: errors.length }));
  process.exitCode = 1;
} finally { await browser.close(); }
