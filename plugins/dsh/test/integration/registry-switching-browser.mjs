// Browser acceptance against the actual isolated Host; no mocked/intercepted responses.
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { chromium } from 'playwright';
const { DSH_PROBE_URL_FILE: entryFile, DSH_PROBE_OUTPUT: output, DSH_SWITCHING_RESULT: resultFile } = process.env;
assert(entryFile && output && resultFile, 'Set URL file, output directory and real Host result file');
const entry = (await readFile(entryFile, 'utf8')).trim();
const fixture = JSON.parse(await readFile(resultFile, 'utf8')); assert(fixture.passed);
const verifyRestart = process.env.DSH_SWITCHING_PHASE === 'verify-restart';
const browser = await chromium.launch({ headless: true, ...(process.env.DSH_PROBE_CHROMIUM ? { executablePath: process.env.DSH_PROBE_CHROMIUM } : {}) });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
const page = await context.newPage(); const other = await context.newPage();
const errors = [];
let stage = 'boot';
for (const tab of [page, other]) tab.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
async function boot(tab, catalog = true) {
  await tab.goto(entry, { waitUntil: 'domcontentloaded' });
  const welcome = tab.getByRole('button', { name: /^(Continue|继续)$/ });
  const later = tab.getByRole('button', { name: /^(Configure later|稍后配置)$/ });
  const center = tab.getByRole('button', { name: '技能中心', exact: true });
  await tab.addLocatorHandler(welcome, async () => { await welcome.click(); });
  await tab.addLocatorHandler(later, async () => { await later.click(); });
  const gettingStarted = tab.getByRole('dialog', { name: /^(Get started|开始使用)$/ });
  await tab.addLocatorHandler(gettingStarted, async () => { await gettingStarted.getByRole('button', { name: /^(Close|关闭)$/ }).click(); });
  if (catalog) {
    await center.click();
    await tab.getByRole('button', { name: '查看当前来源', exact: true }).waitFor();
    assert.equal(await tab.getByRole('combobox', { name: '技能来源', exact: true }).count(), 0);
  } else {
    await tab.getByText(/^(Plugins|插件)$/, { exact: true }).first().click();
  }
}
async function select(tab, label) {
  await tab.getByText(/^(Plugins|插件)$/, { exact: true }).first().click();
  await tab.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
  const form = tab.getByRole('form', { name: 'SkillHub 配置' }); await form.waitFor();
  const radio = label === '未连接来源' ? form.getByRole('radio', { name: '不连接技能来源', exact: false }) : form.getByRole('radio', { name: new RegExp('^选择来源 \\d+：' + label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '$') });
  await radio.check();
  await form.getByRole('button', { name: '保存配置', exact: true }).click();
  await form.getByText('配置已保存。', { exact: true }).waitFor();
  await tab.getByRole('button', { name: '技能中心', exact: true }).click();
}
async function selected(tab, label) {
  const expected = label === '未连接来源' ? '尚未配置来源' : label.startsWith('来源 A') ? 'http://127.0.0.1:43219' : 'http://127.0.0.1:43220';
  await tab.waitForFunction(expected => document.querySelector('[aria-label="查看当前来源"]')?.getAttribute('title') === expected, expected);
}

try {
  stage = 'boot-primary'; await boot(page, verifyRestart);
  if (!verifyRestart) {
    // Loader entry.update only changes runtime config. Exercise the real persisted settings UI.
    stage = 'persist-source-list';
    await page.getByText(/^(Plugins|插件)$/, { exact: true }).first().click();
    await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
    const form = page.getByRole('form', { name: 'SkillHub 配置' }); await form.waitFor();
    const rows = form.locator('.astron-sh-source-entry');
    assert((await rows.count()) <= 3, 'Dedicated fixture must not contain unrelated saved sources');
    while (await rows.count()) await form.getByRole('button', { name: '移除来源 1', exact: true }).click();
    for (const [index, name, port] of [[1, '来源 A', 43219], [2, '来源 B', 43220]]) {
      await form.getByRole('button', { name: '添加来源', exact: true }).click();
      await form.getByLabel(`来源 ${index} 名称`, { exact: true }).fill(name);
      await form.getByLabel(`来源 ${index} 地址`, { exact: true }).fill(`http://127.0.0.1:${port}`);
    }
    await form.getByRole('radio', { name: '选择来源 2：来源 B', exact: true }).check();
    await form.getByRole('button', { name: '保存配置', exact: true }).click();
    await form.getByText('配置已保存。', { exact: true }).waitFor();
    await page.getByRole('button', { name: '技能中心', exact: true }).click();
  }
  stage = 'boot-secondary'; await boot(other);
  stage = 'initial-source'; await selected(page, '来源 B'); await selected(other, '来源 B');
  if (verifyRestart) {
    assert.equal(fixture.resumedWithoutObserver, true, 'Restart must not rerun fixture/config initialization');
    await page.getByText(/^(Plugins|插件)$/, { exact: true }).first().click();
    await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
    const form = page.getByRole('form', { name: 'SkillHub 配置' }); await form.waitFor();
    assert(await form.getByRole('radio', { name: '选择来源 2：来源 B', exact: true }).isChecked());
    await form.getByRole('button', { name: '编辑来源 1', exact: true }).click();
    assert.equal(await form.getByLabel('来源 1 名称', { exact: true }).inputValue(), '来源 A');
    await form.getByRole('button', { name: '编辑来源 2', exact: true }).click();
    assert.equal(await form.getByLabel('来源 2 名称', { exact: true }).inputValue(), '来源 B');
    assert.equal(await form.getByLabel('来源 2 地址', { exact: true }).inputValue(), 'http://127.0.0.1:43220');
    await page.screenshot({ path: join(output, 'registry-switching-restart.png') });
    assert.deepEqual(errors, []);
    const result = { passed: true, realHost: true, modelCalled: false, restartVerified: true, namedSourcesPersisted: true, selectedSourcePersisted: true };
    await writeFile(join(output, 'registry-switching-browser-result.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result));
  } else {
  stage = 'shared-source-switch'; await select(page, '来源 A'); await selected(page, '来源 A'); await selected(other, '来源 A');
  stage = 'installed-list'; await page.getByRole('tab', { name: '已安装', exact: true }).click();
  const installed = page.getByRole('region', { name: '已安装技能', exact: true });
  await installed.locator('.astron-sh-installed-card').nth(1).waitFor();
  assert.equal(await installed.locator('.astron-sh-installed-card').count(), 2, 'Same coordinates from different sources remain separate cards');
  await page.screenshot({ path: join(output, 'registry-switching-installed.png') });
  stage = 'disconnect'; await select(other, '未连接来源'); await selected(page, '未连接来源');
  await installed.getByText('当前来源暂无可检查的技能', { exact: true }).waitFor();
  assert.equal(await installed.locator('.astron-sh-installed-card').count(), 2);
  assert(await installed.getByRole('button', { name: '检查更新', exact: true }).isDisabled());
  await select(other, '来源 B'); await selected(page, '来源 B');
  stage = 'current-source-update'; await installed.getByRole('button', { name: '检查更新', exact: true }).click();
  await installed.getByText('已检查 1/1', { exact: false }).waitFor();
  await page.screenshot({ path: join(output, 'registry-switching-update.png') });
  stage = 'config-cas'; await page.getByText(/^(Plugins|插件)$/, { exact: true }).first().click();
  await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
  const form = page.getByRole('form', { name: 'SkillHub 配置' }); await form.waitFor();
  await form.getByRole('button', { name: '编辑来源 1', exact: true }).click();
  await form.getByLabel('来源 1 名称', { exact: true }).fill('来源 A（保存验证）');
  await select(other, '来源 A');
  await form.getByRole('button', { name: '保存配置', exact: true }).click();
  await form.getByText('配置未保存，可能已被其他页面修改。请重置修改后再试。', { exact: true }).waitFor();
  await form.getByRole('button', { name: '重置修改', exact: true }).click();
  await form.getByRole('button', { name: '编辑来源 1', exact: true }).click();
  await form.getByLabel('来源 1 名称', { exact: true }).fill('来源 A（保存验证）');
  await form.getByRole('button', { name: '保存配置', exact: true }).click();
  await form.getByText('配置已保存。', { exact: true }).waitFor();
  await selected(other, '来源 A（保存验证）');
  await page.screenshot({ path: join(output, 'registry-switching-settings.png') });
  await form.getByRole('button', { name: '编辑来源 1', exact: true }).click();
  await form.getByLabel('来源 1 名称', { exact: true }).fill('来源 A');
  await form.getByRole('button', { name: '保存配置', exact: true }).click();
  await selected(other, '来源 A');
  await select(other, '来源 B');
  await page.getByRole('button', { name: '技能中心', exact: true }).click();
  stage = 'reload';
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: '技能中心', exact: true }).click();
  await selected(page, '来源 B');
  await page.getByRole('tab', { name: '已安装', exact: true }).click();
  await page.getByRole('region', { name: '已安装技能', exact: true }).locator('.astron-sh-installed-card').nth(1).waitFor();
  assert.equal(await page.locator('.astron-sh-installed-card').count(), 2);
  await page.setViewportSize({ width: 760, height: 900 });
  await page.screenshot({ path: join(output, 'registry-switching-narrow.png') });
  assert.deepEqual(errors, []);
  const result = { passed: true, realHost: true, modelCalled: false, twoPageSelection: true, sameNameCardsDistinct: true,
    disconnectedInventoryPreserved: true, currentSourceScanCount: true, staleConfigCasRejected: true, namedConfigSaved: true, reloadPreservesSelection: true,
    screenshots: ['installed', 'update', 'settings', 'narrow'], restartVerified: false };
  await writeFile(join(output, 'registry-switching-browser-result.json'), JSON.stringify(result, null, 2)); console.log(JSON.stringify(result));
  }
} catch (error) {
  await page.screenshot({ path: join(output, 'registry-switching-browser-failure.png') });
  const result = { passed: false, stage, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]').split('\n')[0], errors };
  await writeFile(join(output, 'registry-switching-browser-result.json'), JSON.stringify(result, null, 2)); console.log(JSON.stringify(result)); process.exitCode = 1;
} finally { await browser.close(); }
