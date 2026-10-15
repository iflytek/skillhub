import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output } = process.env;
if (!entry || !output) throw new Error('Set isolated Host and output');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const results = [];
let page;
let stage = "start";
try {
  for (const mode of ['readonly', 'unavailable', 'restored']) {
    page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
    let projected = 0; let writes = 0; const errors = [];
    page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
    page.on('request', request => {
      if (/\/(mutate|startInstall|executePlan|recoverOperation)$/.test(new URL(request.url()).pathname)) writes++;
    });
    await page.route('**/describe', async route => {
      const response = await route.fetch();
      const body = await response.json();
      const view = body.result?.value;
      if (Array.isArray(view?.namespaces) && view.namespaces.some(row => row.ns === 'skillhub')) {
        projected++;
        if (mode === 'readonly') view.writable = false;
        if (mode === 'unavailable') view.namespaces = view.namespaces.filter(row => row.ns !== 'skillhub');
      }
      await route.fulfill({ response, json: body });
    });
    await page.goto(entry, { waitUntil: 'domcontentloaded' });
    stage = `${mode}:welcome`;
    const later = page.getByRole('button', { name: /^(Configure later|稍后配置)$/ });
    await page.addLocatorHandler(later, async locator => { await locator.click(); });
    stage = `${mode}:plugins`;
    await page.getByText('插件', { exact: true }).or(page.getByText('Plugins', { exact: true })).click();
    stage = `${mode}:package`;
    await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
    stage = `${mode}:form`;
    const form = page.getByRole('form', { name: 'SkillHub 配置' });
    if (mode === 'unavailable') {
      await page.getByText('当前连接无法编辑 Host 配置。', { exact: true }).waitFor();
      assert.equal(await form.count(), 0);
      assert.equal(await page.getByRole('button', { name: '保存配置', exact: true }).count(), 0);
    } else {
      await form.waitFor();
      const input = form.getByLabel('Registry 地址', { exact: true });
      if (mode === 'readonly') {
        await form.getByText('当前 Host 配置为只读。', { exact: true }).waitFor();
        assert.equal(await input.isDisabled(), true);
        assert.equal(await form.getByRole('button', { name: '保存配置', exact: true }).isDisabled(), true);
      } else {
        assert.equal(await input.isEnabled(), true);
        await input.fill('http://127.0.0.1:43219/edited');
        assert.equal(await form.getByRole('button', { name: '保存配置', exact: true }).isEnabled(), true);
      }
    }
    assert(projected > 0, 'Settings response was not observed');
    assert.equal(writes, 0); assert.deepEqual(errors, []);
    await page.screenshot({ path: `${output}/config-${mode}.png` });
    results.push({ mode, projected, writes });
    await page.unrouteAll({ behavior: 'wait' });
    await page.close();
  }
  const result = { passed: true, responseProjection: true, freshPagePerState: true, saved: false, results };
  await writeFile(`${output}/config-availability-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  if (page && !page.isClosed()) {
    await page.screenshot({ path: `${output}/config-availability-failure.png` });
    await page.unrouteAll({ behavior: 'wait' });
  }
  console.log(JSON.stringify({ passed: false, stage, error: error.message.split('Call log:')[0], results }));
  process.exitCode = 1;
} finally { await browser.close(); }
