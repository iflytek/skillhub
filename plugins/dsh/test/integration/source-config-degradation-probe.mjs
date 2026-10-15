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
  window.__skillhubConfigInjected = 0;
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
              window.__skillhubConfigInjected++;
              const originalForms = ctx.get('configForms');
              const isolated = ctx.isolate('configForms');
              let remove;
              window.__skillhubConfigProbe = {
                enable: async () => { if (!remove) remove = isolated.provide('configForms', originalForms); },
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
let contextsProjected = 0;
await page.route('**/getContext', async route => {
  const response = await route.fetch();
  const body = await response.json();
  if (body.result?.ok === true && 'configured' in body.result.value) {
    body.result.value.configured = false;
    body.result.value.registryUrl = '';
    contextsProjected++;
  }
  await route.fulfill({ response, json: body });
});
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const later = page.getByRole('button', { name: /^(Configure later|稍后配置)$/ });
  await page.addLocatorHandler(later, async locator => { await locator.click(); });
  await page.getByText(title, { exact: true }).click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场', exact: true });
  const missing = plaza.getByText('尚未配置技能来源，且当前配置入口不可用。请先恢复 DSH 的插件配置功能，再连接 SkillHub。', { exact: true });
  const available = plaza.getByText('在 DSH 的插件页面打开 SkillHub 配置，填写来源地址并保存。', { exact: true });
  await missing.waitFor();
  injected = await page.evaluate(() => window.__skillhubConfigInjected);
  assert.equal(injected, 1);
  for (let cycle = 0; cycle < 2; cycle++) {
    assert.equal(await available.count(), 0);
    await page.evaluate(() => window.__skillhubConfigProbe.enable());
    await available.waitFor();
    assert.equal(await missing.count(), 0);
    await page.evaluate(() => window.__skillhubConfigProbe.disable());
    await missing.waitFor();
  }
  assert(contextsProjected > 0);
  assert.equal(writes, 0); assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/source-config-degradation.png` });
  const result = { passed: true, scopedConfigFormsIsolation: true, sourceContextProjected: true,
    samePageRecoveryCycles: 2, contextsProjected, browserWrites: writes, modelInvoked: false };
  await writeFile(`${output}/source-config-degradation-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/source-config-degradation-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.split('Call log:')[0], injected, contextsProjected, writes, pageErrorCount: errors.length }));
  process.exitCode = 1;
} finally { await page.unrouteAll({ behavior: 'wait' }); await browser.close(); }
