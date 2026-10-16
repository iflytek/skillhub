import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_SEARCH_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host URL, output and fixture title');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = [];
const held = [];
let writes = 0;
let changed = false;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => { if (/\/(startInstall|executePlan)$/.test(new URL(request.url()).pathname)) writes++; });
const original = 'http://127.0.0.1:43219';
const other = 'http://127.0.0.1:43220';
const hold = async method => {
  const reached = Promise.withResolvers();
  const release = Promise.withResolvers();
  const finished = Promise.withResolvers();
  let first = true;
  await page.route('**/' + method, async route => {
    if (!first) return route.continue();
    first = false;
    try {
      const response = await route.fetch();
      assert.equal((await response.json()).result.ok, true);
      reached.resolve();
      await release.promise;
      try { await route.fulfill({ response }); finished.resolve('delivered'); }
      catch { finished.resolve('discarded-after-cancel'); }
    } catch (error) { reached.reject(error); finished.reject(error); }
  });
  const pending = { reached: reached.promise, release: () => release.resolve(), finished: finished.promise };
  held.push(pending); return pending;
};
const source = async address => {
  await page.getByText('Plugins', { exact: true }).first().click();
  await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
  const form = page.getByRole('form', { name: 'SkillHub 配置' });
  await form.getByRole('textbox', { name: 'Registry 地址' }).fill(address);
  await form.getByRole('button', { name: '保存配置', exact: true }).click();
  await form.getByText('配置已保存。', { exact: true }).waitFor();
  changed = address !== original;
};
const card = page.getByRole('region', { name: 'SkillHub 搜索结果' });
const showCard = async () => {
  await page.getByText(title, { exact: true }).click();
  if (!await card.isVisible()) {
    const turn = page.locator('button[data-turn-process]:enabled').last();
    if (await turn.getAttribute('aria-expanded') === 'false') await turn.click();
  }
  if (!await card.isVisible()) await page.getByText('Called tools', { exact: true }).click();
  await card.waitFor();
};
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  if (!await page.getByText(title, { exact: true }).waitFor({ timeout: 3000 }).then(() => true, () => false)) {
    await page.getByText(process.env.DSH_SEARCH_FIXTURE_WORKSPACE, { exact: true }).click();
  }
  await showCard();
  const oldDetail = await hold('openConversationDetail');
  await card.getByRole('button', { name: '查看技能详情', exact: true }).click();
  await oldDetail.reached;
  const drawer = page.getByRole('dialog', { name: '技能详情', exact: true });
  await drawer.getByRole('button', { name: '关闭技能详情', exact: true }).click();
  await source(other);
  await showCard();
  await card.getByRole('button', { name: '查看技能详情', exact: true }).click();
  const stale = drawer.getByText('这条搜索记录已失效或不属于当前会话，请重新搜索。', { exact: true });
  await stale.waitFor();
  oldDetail.release();
  const detailTransport = await oldDetail.finished;
  await stale.waitFor();
  assert.equal(await drawer.getByRole('button', { name: /安装到/ }).count(), 0);
  await page.screenshot({ path: `${output}/source-race-detail.png` });
  await drawer.getByRole('button', { name: '关闭技能详情', exact: true }).click();
  const oldList = await hold('listCatalog');
  await page.getByText('技能中心', { exact: true }).click();
  await oldList.reached;
  await source(original);
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByText(original, { exact: true }).waitFor();
  const current = plaza.getByRole('button', { name: /document-helper/ });
  await current.waitFor();
  assert((await current.innerText()).includes(process.env.DSH_FIXTURE_NAMESPACE));
  oldList.release();
  const listTransport = await oldList.finished;
  await page.waitForTimeout(200);
  assert((await current.innerText()).includes(process.env.DSH_FIXTURE_NAMESPACE));
  assert(!(await current.innerText()).includes('@demo/'));
  assert.equal(writes, 0); assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/source-race-list.png` });
  const result = { passed: true, lateDetailRejected: true, lateListRejected: true, detailTransport, listTransport,
    originalSourceRestored: !changed, writeRequests: writes, errors };
  await writeFile(`${output}/source-race-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/source-race-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally {
  for (const pending of held) pending.release();
  if (changed) await source(original);
  await browser.close();
}
