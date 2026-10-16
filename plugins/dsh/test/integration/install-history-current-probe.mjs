import { expandInstallRecord } from './receipt-disclosure.mjs';
import { selectValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_SEARCH_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host, install history title and output');
const restartBrowser = process.env.DSH_PROBE_RESTART_BROWSER === '1';
let browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
let page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = []; let installs = 0; let removals = 0;
function observe() {
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => {
  const path = new URL(request.url()).pathname;
  if (path.endsWith('/startInstall')) installs++;
  if (path.endsWith('/executePlan')) removals++;
});
}
observe();
let card = page.getByRole('region', { name: 'SkillHub 安装结果', exact: true });
async function expand() {
  if (!await card.isVisible()) {
    const process = page.locator('button[data-turn-process="1"]');
    if (await process.getAttribute('aria-expanded') === 'false') await process.click();
  }
  if (!await card.isVisible()) await page.getByText('Called tools', { exact: true }).click();
  await card.getByText(/^(技能文件安装完成。|历史安装完成。)$/).waitFor();
  await expandInstallRecord(card);
  await card.getByText('原操作回执不代表当前仍安装此版本，当前状态以下方核验为准。', { exact: true }).waitFor();
}
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  await page.getByText(title, { exact: true }).click(); await expand();
  let current = card.getByRole('region', { name: '当前安装记录' });
  await current.getByText('当前项目 · 1.2.0 · 文件存在', { exact: true }).waitFor();
  await card.getByRole('button', { name: '在已安装中查看', exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await selectValue(plaza.getByRole('combobox', { name: '筛选安装范围' }), 'project');
  await plaza.getByRole('button', { name: '查看已安装详情', exact: true }).click();
  await plaza.getByRole('button', { name: '卸载此安装位置', exact: true }).click();
  await page.getByRole('dialog', { name: '确认卸载技能', exact: true }).getByRole('button', { name: '确认卸载', exact: true }).click();
  await plaza.getByText('此安装位置已卸载。', { exact: true }).waitFor();
  await plaza.getByRole('button', { name: '返回会话', exact: true }).click(); await expand();
  await current.getByText('当前会话可见范围内已无此技能的安装记录。', { exact: true }).waitFor();
  await card.getByText(/^(技能文件安装完成。|历史安装完成。)$/).waitFor();
  if (restartBrowser) {
    await browser.close();
    browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
    page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
    observe();
    card = page.getByRole('region', { name: 'SkillHub 安装结果', exact: true });
    current = card.getByRole('region', { name: '当前安装记录' });
    await page.goto(entry, { waitUntil: 'domcontentloaded' });
    assert.equal(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('skillhub:')).length), 0);
    await page.getByRole('button', { name: 'Configure later', exact: true }).click();
    await page.getByText(title, { exact: true }).click();
    await expand();
  } else { await page.reload({ waitUntil: 'domcontentloaded' }); await expand(); }
  await card.getByText(/^(技能文件安装完成。|历史安装完成。)$/).waitFor();
  await current.getByText('当前会话可见范围内已无此技能的安装记录。', { exact: true }).waitFor();
  await current.scrollIntoViewIfNeeded();
  await page.screenshot({ path: `${output}/history-after-removal.png` });
  assert.equal(installs, 0); assert.equal(removals, 1); assert.deepEqual(errors, []);
  const result = { passed: true, historicalReceiptPreserved: true, currentAbsenceVerified: true, reloadVerified: !restartBrowser, browserRestartVerified: restartBrowser, installs, removals, errors };
  await writeFile(`${output}/history-current-result.json`, JSON.stringify(result, null, 2)); console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/history-current-failure.png` });
  console.log(JSON.stringify({ error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors })); process.exitCode = 1;
} finally { await browser.close(); }
