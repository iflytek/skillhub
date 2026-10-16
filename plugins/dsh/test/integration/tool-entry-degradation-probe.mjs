import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host, output and install fixture title');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
let injected = 0; let writes = 0; const errors = [];
page.on('pageerror', error => errors.push(error.message));
page.on('request', request => { if (/\/(startInstall|executePlan|recoverOperation)$/.test(new URL(request.url()).pathname)) writes++; });
await page.addInitScript(() => {
  let loader;
  window.__skillhubToolInjected = 0;
  Object.defineProperty(window, '__ModuleLoader__', { configurable: true,
    get: () => loader,
    set: value => {
      loader = value;
      const wrapLoad = load => function(entry) {
        if (entry.id === '@deepseek-ai/dsh-client-ui-tool') {
          const factory = entry.factory;
          entry = { ...entry, factory: require => {
            const module = factory(require);
            return { ...module, apply: async ctx => {
              window.__skillhubToolInjected++;
              let fiber;
              window.__skillhubToolProbe = {
                enable: async () => { if (!fiber) { fiber = ctx.plugin(module); await fiber; } },
                disable: async () => { if (fiber) { await fiber.dispose(); fiber = undefined; } },
              };
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
  const later = page.getByRole('button', { name: /^(Configure later|稍后配置)$/ });
  await page.addLocatorHandler(later, async locator => { await locator.click(); });
  await page.getByText(title, { exact: true }).click();
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场', exact: true });
  const notice = plaza.getByRole('status', { name: 'SkillHub 部分可用', exact: true });
  const message = notice.getByText('对话技能卡片暂不可用，可在技能广场浏览和管理技能。', { exact: true });
  await message.waitFor();
  injected = await page.evaluate(() => window.__skillhubToolInjected);
  assert.equal(injected, 1);
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  await plaza.getByRole('heading', { name: 'Document helper', exact: true }).waitFor();
  await plaza.getByRole('tab', { name: '版本', exact: true }).click();
  await plaza.getByText('改进文档提取', { exact: true }).waitFor();
  await plaza.getByRole('button', { name: '← 返回技能广场', exact: true }).click();
  for (let cycle = 0; cycle < 2; cycle++) {
    await page.evaluate(() => window.__skillhubToolProbe.enable());
    await notice.waitFor({ state: 'hidden' });
    await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
    await plaza.getByRole('region', { name: '已安装技能', exact: true }).waitFor();
    await plaza.getByRole('tab', { name: '浏览技能', exact: true }).click();
    await page.evaluate(() => window.__skillhubToolProbe.disable());
    await message.waitFor();
    await plaza.getByRole('button', { name: /document-helper/ }).waitFor();
  }
  assert.equal(writes, 0); assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/tool-entry-degradation.png` });
  const result = { passed: true, officialToolModuleApplyWithheld: true, catalogDetailAndVersions: true,
    partialAvailabilityNotice: true, restoredCycles: 2, browserWrites: writes, modelInvoked: false };
  await writeFile(`${output}/tool-entry-degradation-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/tool-entry-degradation-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.split('Call log:')[0], injected, writes, pageErrorCount: errors.length }));
  process.exitCode = 1;
} finally { await browser.close(); }
