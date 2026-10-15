import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host, output and install fixture title');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const searchOnly = process.env.DSH_LAYOUT_SEARCH_PROBE === '1';
let injected = 0; let writes = 0; const errors = [];
page.on('pageerror', error => errors.push(error.message));
page.on('request', request => { if (/\/(startInstall|executePlan|recoverOperation)$/.test(new URL(request.url()).pathname)) writes++; });
await page.addInitScript(() => {
  let loader;
  window.__skillhubLayoutInjected = 0;
  Object.defineProperty(window, '__ModuleLoader__', { configurable: true,
    get: () => loader,
    set: value => {
      loader = value;
      const wrapLoad = load => function(entry) {
        if (entry.id === '@astron-team/skillhub-dsh') {
          const factory = entry.factory;
          entry = { ...entry, factory: require => {
            const module = factory(require);
            return { ...module, apply: async ctx => {
              window.__skillhubLayoutInjected++;
              const originalLayout = ctx.get('layout');
              const isolated = ctx.isolate('layout');
              let remove;
              window.__skillhubLayoutProbe = {
                enable: async () => { if (!remove) remove = isolated.provide('layout', originalLayout); },
                disable: async () => { if (remove) { await remove(); remove = undefined; } },
              };
              return module.apply(isolated);
            } };
          } };
        }
        return load.call(this, entry);
      };
      let wrapped = wrapLoad(value.load);
      Object.defineProperty(value, 'load', { configurable: true,
        get: () => wrapped, set: load => { wrapped = wrapLoad(load); },
      });
    },
  });
});
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: /^(Configure later|稍后配置)$/ }).click();
  await page.getByText(title, { exact: true }).click();
  if (searchOnly) {
    const card = page.getByRole('region', { name: 'SkillHub 搜索结果', exact: true });
    const turn = page.locator('button[data-turn-process]:enabled').last();
    if (await turn.getAttribute('aria-expanded') === 'false') await turn.click();
    if (!await card.isVisible()) await page.getByText(/^(Called tools|调用的工具|已调用工具)$/).click();
    await card.waitFor();
    injected = await page.evaluate(() => window.__skillhubLayoutInjected);
    assert.equal(injected, 1);
    assert.equal(await page.getByText('SkillHub', { exact: true }).count(), 0);
    for (let cycle = 0; cycle < 2; cycle++) {
      const acknowledgment = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/acknowledgeConversationDetail'));
      await card.getByRole('button', { name: '查看技能详情', exact: true }).click();
      const drawer = page.getByRole('dialog', { name: '技能详情', exact: true });
      await drawer.getByRole('heading', { name: 'Document helper', exact: true }).waitFor();
      const confirmed = await (await acknowledgment).json();
      assert.equal(confirmed.result?.ok, true);
      assert.equal(confirmed.result.value, true);
      await drawer.getByRole('tab', { name: '版本', exact: true }).click();
      await drawer.getByText('改进文档提取', { exact: true }).waitFor();
      await page.keyboard.press('Escape');
      await drawer.waitFor({ state: 'detached' });
      await page.evaluate(() => window.__skillhubLayoutProbe.enable());
      await page.getByText('SkillHub', { exact: true }).waitFor();
      await page.evaluate(() => window.__skillhubLayoutProbe.disable());
      await page.getByText('SkillHub', { exact: true }).waitFor({ state: 'hidden' });
      await card.waitFor();
    }
    assert.equal(writes, 0); assert.deepEqual(errors, []);
    await page.screenshot({ path: `${output}/layout-search-degradation.png` });
    const result = { passed: true, installedClientScopedLayoutIsolation: true, searchCardWithoutLayout: true,
      detailAcknowledgmentWithoutLayout: true, versionTabWithoutLayout: true, layoutRecoveryCycles: 2,
      browserWrites: writes, modelInvoked: false };
    await writeFile(`${output}/layout-search-degradation-result.json`, JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result));
  } else {
  const card = page.getByRole('region', { name: 'SkillHub 安装结果', exact: true });
  const turn = page.locator('button[data-turn-process]:enabled').last();
  if (await turn.getAttribute('aria-expanded') === 'false') await turn.click();
  if (!await card.isVisible()) await page.getByText(/^(Called tools|调用的工具|已调用工具)$/).click();
  await card.getByText('技能文件安装完成。', { exact: true }).waitFor();
  injected = await page.evaluate(() => window.__skillhubLayoutInjected);
  assert.equal(injected, 1);
  assert.equal(await page.getByText('SkillHub', { exact: true }).count(), 0);
  await page.getByRole('status', { name: 'SkillHub 部分可用', exact: true }).getByText('技能广场入口暂不可用，可继续使用对话中的技能卡片。', { exact: true }).waitFor();
  await card.getByRole('button', { name: '在已安装中查看', exact: true }).click();
  await card.getByText('SkillHub 广场入口暂不可用，请在此卡片查询原操作。', { exact: true }).waitFor();
  await card.getByRole('button', { name: '查询原操作', exact: true }).click();
  await card.getByText('技能文件安装完成。', { exact: true }).waitFor();
  for (let cycle = 0; cycle < 2; cycle++) {
    await page.evaluate(() => window.__skillhubLayoutProbe.enable());
    await page.getByText('SkillHub', { exact: true }).waitFor();
    await page.getByRole('status', { name: 'SkillHub 部分可用', exact: true }).waitFor({ state: 'hidden' });
    await card.getByRole('button', { name: '在已安装中查看', exact: true }).click();
    await page.getByRole('button', { name: '返回会话', exact: true }).waitFor();
    await page.getByRole('button', { name: '返回会话', exact: true }).click();
    if (!await card.isVisible()) await page.getByText(/^(Called tools|调用的工具|已调用工具)$/).click();
    await card.waitFor();
    await page.evaluate(() => window.__skillhubLayoutProbe.disable());
    await page.getByText('SkillHub', { exact: true }).waitFor({ state: 'hidden' });
    await card.getByText('技能文件安装完成。', { exact: true }).waitFor();
  }
  assert.equal(writes, 0); assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/layout-degradation.png` });
  const result = { passed: true, installedClientScopedLayoutIsolation: true, operationReadableWithoutLayout: true,
    missingLayoutMessage: true, partialAvailabilityNotice: true, restoredNavigationCycles: 2, browserWrites: writes, modelInvoked: false };
  await writeFile(`${output}/layout-degradation-result.json`, JSON.stringify(result, null, 2)); console.log(JSON.stringify(result));
  }
} catch (error) {
  await page.screenshot({ path: `${output}/layout-degradation-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message, injected, writes, errors })); process.exitCode = 1;
} finally { await browser.close(); }
