import { selectValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_DUAL_ROOT: root } = process.env;
if (!entry || !output || !root) throw new Error('Set isolated Host, output and dual fixture root');
const { title } = JSON.parse(await readFile(`${root}/dual-ready.json`, 'utf8'));
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
let submissions = 0;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/startInstall')) submissions++; });
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  await page.getByText(title, { exact: true }).click();
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  await selectValue(plaza.getByRole('combobox', { name: '安装范围', exact: true }), 'project');
  const accepted = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/startInstall'));
  await plaza.getByRole('button', { name: '安装到当前项目', exact: true }).click();
  await page.getByRole('dialog', { name: '确认安装技能？', exact: true }).getByRole('button', { name: '确认安装', exact: true }).click();
  const receipt = (await (await accepted).json()).result;
  assert.equal(receipt.ok, true);
  await writeFile(`${root}/dual-trigger.json`, JSON.stringify({ operationId: receipt.value.operationId }));
  await plaza.getByText('已安装此版本', { exact: true }).waitFor();
  const deadline = Date.now() + 60000;
  let result;
  for (;;) {
    try { result = JSON.parse(await readFile(`${root}/dual-result.json`, 'utf8')); break; }
    catch (error) { if (error.code !== 'ENOENT') throw error; }
    assert(Date.now() < deadline); await delay(100);
  }
  assert.equal(result.passed, true, JSON.stringify(result));
  await plaza.getByRole('button', { name: '返回会话', exact: true }).click();
  // Read the persisted synthetic transcript through the normal session reload path.
  await page.reload({ waitUntil: 'domcontentloaded' });
  const card = page.getByRole('region', { name: 'SkillHub 安装结果', exact: true });
  if (!await card.isVisible()) {
    const process = page.locator('button[data-turn-process]:enabled').last();
    await process.waitFor({ state: 'visible' });
    await process.click();
  }
  if (!await card.isVisible()) await page.getByText('Called tools', { exact: true }).last().click();
  await card.getByText('本次安装未完成。无需恢复文件。', { exact: true }).waitFor();
  await card.getByText('本次请求失败不代表技能未安装，请以下方当前安装记录为准。', { exact: true }).waitFor();
  await card.getByRole('region', { name: '当前安装记录' }).getByText('当前项目 · 1.2.0 · 文件存在', { exact: true }).waitFor();
  assert.equal(await card.getByText('技能文件安装完成。', { exact: true }).count(), 0);
  result.conflictCardVerified = true;
  assert.equal(submissions, 1);
  assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/concurrent-install.png` });
  await writeFile(`${output}/concurrent-browser-result.json`, JSON.stringify({ ...result, submissions, errors }, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/concurrent-failure.png` });
  await writeFile(`${output}/process-buttons.json`, JSON.stringify(await page.locator('button[data-turn-process]').evaluateAll(buttons => buttons.map(button => ({ disabled: button.disabled, expanded: button.getAttribute('aria-expanded'), tools: button.getAttribute('data-turn-process-tool-calls') })))));
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await browser.close(); }
