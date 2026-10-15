import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output } = process.env;
if (!entry || !output) throw new Error('Set isolated Host and output directory; Registry requires DSH_FIXTURE_PAGINATION=1');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
const pages = [];
const failureRetry = process.env.DSH_PROBE_FAILURE_RETRY === '1';
const failedMethods = new Set();
let writes = 0;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => {
  const path = new URL(request.url()).pathname;
  if (/\/(startInstall|executePlan|recoverOperation)$/.test(path)) writes++;
  if (/\/(listVersions|listReviews)$/.test(path)) pages.push({ method: path.split('/').pop(), page: request.postDataJSON().payload.args.page });
});
try {
  if (failureRetry) await page.route(/\/(listVersions|listReviews)$/, async route => {
    const method = new URL(route.request().url()).pathname.split('/').pop();
    if (route.request().postDataJSON().payload.args.page === 1 && !failedMethods.has(method)) {
      failedMethods.add(method); await route.abort('failed');
    } else await route.continue();
  });
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  await page.setViewportSize({ width: 390, height: 844 });
  const layouts = [];
  for (const [tab, last] of [['版本', '历史版本 20'], ['评论', '评论内容 20']]) {
    await plaza.getByRole('tab', { name: tab, exact: true }).click();
    const nav = plaza.getByRole('navigation', { name: `${tab}分页` });
    await nav.getByText('第 1 页 · 共 21 项', { exact: true }).waitFor();
    assert.equal(await nav.getByRole('button', { name: '上一页', exact: true }).isDisabled(), true);
    await nav.getByRole('button', { name: '下一页', exact: true }).click();
    if (failureRetry) {
      const error = plaza.getByRole('tabpanel').getByRole('alert');
      await error.getByRole('button', { name: '重试', exact: true }).waitFor();
      assert.equal(await plaza.getByText(`暂无${tab}信息。`, { exact: true }).count(), 0);
      await page.waitForTimeout(1200);
      const method = tab === '版本' ? 'listVersions' : 'listReviews';
      assert.deepEqual(pages.filter(item => item.method === method).map(item => item.page), [0, 1], 'Failed page must not retry without user action');
      await page.screenshot({ path: `${output}/module-${method}-failed.png` });
      await error.getByRole('button', { name: '重试', exact: true }).click();
    }
    await plaza.getByText(last, { exact: true }).waitFor();
    await nav.getByText('第 2 页 · 共 21 项', { exact: true }).waitFor();
    assert.equal(await nav.getByRole('button', { name: '下一页', exact: true }).isDisabled(), true);
    const labelLines = await nav.getByRole('button').evaluateAll(elements => elements.map(element => {
      const range = document.createRange(); range.selectNodeContents(element);
      return new Set([...range.getClientRects()].map(rect => rect.top)).size;
    }));
    assert.deepEqual(labelLines, [1, 1], 'Pagination button labels must remain on one line');
    const layout = await plaza.evaluate(element => ({ width: element.clientWidth, scrollWidth: element.scrollWidth }));
    assert.ok(layout.scrollWidth <= layout.width, `${tab} pagination overflows on mobile`);
    layouts.push({ tab, ...layout });
    await page.screenshot({ path: `${output}/module-${tab === '版本' ? 'versions' : 'reviews'}-page2.png` });
    await nav.getByRole('button', { name: '上一页', exact: true }).click();
    await nav.getByText('第 1 页 · 共 21 项', { exact: true }).waitFor();
  }
  for (const method of ['listVersions', 'listReviews']) assert.deepEqual(pages.filter(item => item.method === method).map(item => item.page), failureRetry ? [0, 1, 1, 0] : [0, 1, 0]);
  assert.equal(writes, 0);
  assert.deepEqual(errors, []);
  const result = { passed: true, failureRetry, pages, layouts, writes, errors };
  await writeFile(`${output}/pagination-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/pagination-failure.png` });
  console.log(JSON.stringify({ error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), pages, errors }));
  process.exitCode = 1;
} finally { await browser.close(); }
