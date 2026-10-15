import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host, output and persisted install fixture title');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
await page.addInitScript(() => {
  const listeners = new Map();
  const add = EventTarget.prototype.addEventListener;
  const remove = EventTarget.prototype.removeEventListener;
  const key = (target, options) => `${target === document ? 'document' : 'window'}/${typeof options === 'boolean' ? options : !!options?.capture}`;
  EventTarget.prototype.addEventListener = function(type, listener, options) {
    if (type === 'visibilitychange' && (this === document || this === window) && listener) {
      const keys = listeners.get(listener) ?? new Set(); keys.add(key(this, options)); listeners.set(listener, keys);
    }
    return add.call(this, type, listener, options);
  };
  EventTarget.prototype.removeEventListener = function(type, listener, options) {
    if (type === 'visibilitychange' && (this === document || this === window)) {
      const keys = listeners.get(listener); keys?.delete(key(this, options)); if (!keys?.size) listeners.delete(listener);
    }
    return remove.call(this, type, listener, options);
  };
  window.__skillhubVisibilityCount = () => [...listeners.values()].reduce((total, keys) => total + keys.size, 0);
});
const operationResponses = [];
page.on('response', async response => {
  if (!new URL(response.url()).pathname.endsWith('/getOperation')) return;
  try { const body = await response.json(); const result = body.result;
    operationResponses.push({ status: response.status(), ok: result?.ok, code: result?.error?.code, phase: result?.value?.phase });
  } catch { operationResponses.push({ status: response.status(), unreadable: true }); }
});
const errors = []; const counts = []; let queries = 0; let disabled = false; let manualReadRecovery = 0;
page.on('pageerror', e => errors.push(e.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', r => { if (new URL(r.url()).pathname.endsWith('/getOperation')) queries++; });
const count = () => page.evaluate(() => window.__skillhubVisibilityCount());
const settings = async () => {
  await page.getByText(/^(Plugins|插件)$/).first().click();
  await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
  return page.getByRole('switch', { name: /(?:Enable|启用).*skillhub/i }).first();
};
const waitCount = async expected => {
  await page.waitForFunction(value => window.__skillhubVisibilityCount() === value, expected);
};
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: /^(Configure later|稍后配置)$/ }).click();
  await settings();
  const baseline = await count();
  for (let cycle = 0; cycle < 3; cycle++) {
    if (!await page.getByText(title, { exact: true }).isVisible()) {
      await page.getByText(process.env.DSH_INSTALL_FIXTURE_WORKSPACE, { exact: true }).click();
    }
    await page.getByText(title, { exact: true }).click();
    const card = page.getByRole('region', { name: 'SkillHub 安装结果' });
    if (!await card.isVisible()) {
      const turn = page.locator('button[data-turn-process]:enabled').last();
      if (await turn.getAttribute('aria-expanded') === 'false') await turn.click();
    }
    if (!await card.isVisible()) await page.getByText(/^(Called tools|调用的工具|已调用工具)$/).click();
    const completion = card.getByText('技能文件安装完成。', { exact: true });
    const unavailable = card.getByText('原操作结果无法核验，请勿重复安装。', { exact: true });
    await completion.or(unavailable).waitFor();
    if (await unavailable.isVisible()) {
      // Bounded automatic retries can expire while the Host reloads. Exercise the supported manual read.
      await card.getByRole('button', { name: '查询原操作', exact: true }).click();
      manualReadRecovery++;
    }
    await completion.waitFor();
    await card.getByRole('button', { name: '刷新发现状态', exact: true }).waitFor({ state: 'visible' });
    if (cycle === 0) {
      // Keep the actual operation unchanged; only its read projection is held pending.
      let projected = 0;
      const pending = async route => {
        const response = await route.fetch();
        const body = await response.json();
        assert.equal(body.result?.ok, true);
        body.result.value.phase = 'staging';
        body.result.value.files = 'pending';
        projected++;
        await route.fulfill({ response, json: body });
      };
      await page.route('**/getOperation', pending);
      await card.getByRole('button', { name: '刷新发现状态', exact: true }).click();
      const deadline = Date.now() + 10000;
      while (projected < 2) {
        assert(Date.now() < deadline, 'Pending operation must actually poll before hiding');
        await page.waitForTimeout(100);
      }
      await page.evaluate(() => {
        Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' });
        document.dispatchEvent(new Event('visibilitychange'));
      });
      await page.waitForTimeout(200);
      const hiddenQueries = queries;
      await page.waitForTimeout(2500);
      assert.equal(queries, hiddenQueries, 'Hidden pending operation must pause polling');
      await page.unroute('**/getOperation', pending);
      await page.evaluate(() => {
        delete document.visibilityState;
        document.dispatchEvent(new Event('visibilitychange'));
      });
      await completion.waitFor();
      assert(queries > hiddenQueries, 'Showing the document must refresh the operation');
    }
    const mounted = await count(); assert(mounted > baseline, 'The probe must observe actual mounted subscriptions');
    const toggle = await settings();
    await waitCount(baseline);
    const beforeDisable = queries;
    await toggle.click(); disabled = true;
    await page.getByText('SkillHub', { exact: true }).waitFor({ state: 'hidden' });
    await waitCount(baseline);
    await page.waitForTimeout(1500);
    assert.equal(queries, beforeDisable, 'Unmounted operation view must not poll');
    counts.push({ cycle, baseline, mounted, disabled: await count() });
    await toggle.click(); disabled = false;
    await page.getByText('SkillHub', { exact: true }).waitFor();
    assert.equal(await page.getByText('SkillHub', { exact: true }).count(), 1);
  }
  assert.equal(new Set(counts.map(c => c.mounted)).size, 1, 'Repeated mounts must not accumulate listeners');
  assert.deepEqual(errors, []);
  const result = { passed: true, cycles: counts, operationResponses, hiddenPendingPollingPaused: true, visibleRefresh: true,
    pendingProjectionInjected: true, noPollingAfterUnmount: true, oneRestoredEntry: true, manualReadRecovery, errors };
  await writeFile(`${output}/subscription-lifecycle-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/subscription-lifecycle-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), counts, operationResponses, errors }));
  process.exitCode = 1;
} finally {
  if (disabled) { const toggle = await settings(); await toggle.click(); }
  await browser.close();
}
