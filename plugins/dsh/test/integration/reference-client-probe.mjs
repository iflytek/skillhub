import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output } = process.env;
if (!entry || !output) throw new Error('Set isolated Host URL and output');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
let stage = 'start';
let restoreToggle;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
await page.addInitScript(() => {
  let loader;
  window.__coexistContexts = {};
  Object.defineProperty(window, '__ModuleLoader__', { configurable: true, get: () => loader, set: value => {
    loader = value;
    const wrap = load => function(entry) {
      if (['@astron-team/skillhub-dsh', '@cocofhu/skillhub'].includes(entry.id)) {
        const factory = entry.factory;
        entry = { ...entry, factory: require => {
          const module = factory(require);
          return { ...module, apply: ctx => {
            window.__coexistContexts[entry.id] = ctx;
            return module.apply(ctx);
          } };
        } };
      }
      return load.call(this, entry);
    };
    let wrapped = wrap(value.load);
    Object.defineProperty(value, 'load', { configurable: true, get: () => wrapped,
      set: load => { wrapped = wrap(load); } });
  } });
});
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const later = page.getByRole('button', { name: /^(Configure later|稍后配置)$/ });
  await page.addLocatorHandler(later, async locator => { await locator.click(); });
  const preview = page.getByRole('button', { name: /^(Continue|继续)$/ });
  await page.addLocatorHandler(preview, async locator => { await locator.click(); });
  stage = 'both-clients';
  await page.waitForFunction(() => window.__coexistContexts['@astron-team/skillhub-dsh']
    && window.__coexistContexts['@cocofhu/skillhub']);
  stage = 'catalog';
  await page.getByText('技能中心', { exact: true }).click();
  await page.getByRole('region', { name: 'SkillHub 技能广场' }).getByRole('button', { name: /document-helper/ }).waitFor();
  const registrations = await page.evaluate(() => {
    const slots = window.__coexistContexts['@astron-team/skillhub-dsh'].slots;
    const names = ['main', 'sidebar.panellist', 'sidebar.footer.action', 'tool.call.toolview', 'plugins.bundle.config', 'settings.plugin.item'];
    return Object.fromEntries(names.map(name => [name, slots.entriesOfSlot(name).map(row => ({ key: row.options.key, id: row.options.id }))]));
  });
  const tools = registrations['tool.call.toolview'].map(row => row.key);
  for (const name of ['astron_skillhub_search', 'astron_skillhub_install', 'skillhub_search', 'skillhub_list', 'skillhub_plugin_search']) {
    assert.equal(tools.filter(key => key === name).length, 1, `Distinct tool view: ${name}`);
  }
  assert.equal(registrations.main.filter(row => row.key === 'skillhub').length, 1);
  assert.equal(registrations['sidebar.panellist'].filter(row => row.id === 'skillhub').length, 1);
  await page.evaluate(() => {
    const slots = window.__coexistContexts['@cocofhu/skillhub'].slots;
    window.__referenceViews = ['sidebar.footer.action', 'tool.call.toolview'].flatMap(name =>
      slots.entriesOfSlot(name).filter(row => String(row.options.key ?? row.options.id).startsWith('skillhub'))
        .map(row => ({ name, row })));
  });
  const unchanged = async () => {
    const result = await page.evaluate(() => {
      const slots = window.__coexistContexts['@cocofhu/skillhub'].slots;
      return { count: window.__referenceViews.length,
        unchanged: window.__referenceViews.every(({ name, row }) => slots.entriesOfSlot(name).includes(row)) };
    });
    assert.equal(result.count, 4);
    assert.equal(result.unchanged, true);
  };
  for (let cycle = 0; cycle < 2; cycle++) {
    await page.getByText('Plugins', { exact: true }).or(page.getByText('插件', { exact: true })).first().click();
    await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
    const toggle = page.getByRole('switch').first();
    stage = `cycle-${cycle}:disable`;
    restoreToggle = toggle;
    assert.equal(await toggle.getAttribute('aria-checked'), 'true');
    await toggle.click();
    await page.getByText('技能中心', { exact: true }).waitFor({ state: 'hidden' });
    const deadline = Date.now() + 15000;
    while (await toggle.getAttribute('aria-checked') !== 'false') {
      assert(Date.now() < deadline, 'Disable response did not settle');
      await delay(50);
    }
    await unchanged();
    await page.getByText('Plugin Plaza', { exact: true }).or(page.getByText('插件广场', { exact: true })).waitFor();
    stage = `cycle-${cycle}:enable`;
    await toggle.click();
    stage = `cycle-${cycle}:catalog-restored`;
    await page.getByText('技能中心', { exact: true }).click();
    restoreToggle = undefined;
    await page.getByRole('region', { name: 'SkillHub 技能广场' }).getByRole('button', { name: /document-helper/ }).waitFor();
    await unchanged();
  }
  assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/reference-client-coexistence.png` });
  await writeFile(`${output}/reference-client-result.json`, JSON.stringify({ passed: true, cycles: 2, referenceViewsUnchanged: 4, registrations, errors,
    modelInvoked: false, referenceMarketUsed: false }, null, 2));
} catch (error) {
  await page.screenshot({ path: `${output}/reference-client-failure.png` });
  await writeFile(`${output}/reference-client-result.json`, JSON.stringify({ passed: false, stage,
    error: error.message.split('Call log:')[0].replace(/token=[^\s&]+/g, 'token=[redacted]'), errors,
    clients: await page.evaluate(() => Object.keys(window.__coexistContexts ?? {})) }, null, 2));
  process.exitCode = 1;
} finally {
  if (restoreToggle) {
    try {
      if (await restoreToggle.getAttribute('aria-checked') === 'false') await restoreToggle.click();
      await page.getByText('技能中心', { exact: true }).waitFor();
    } catch { process.exitCode = 1; }
  }
  await browser.close();
}
