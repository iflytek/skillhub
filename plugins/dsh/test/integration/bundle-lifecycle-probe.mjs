import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output } = process.env;
if (!entry || !output) throw new Error('Set isolated Host URL and output');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
const errors = []; const changes = [];
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('response', response => {
  if (new URL(response.url()).pathname.endsWith('/setBundleEnabled')) changes.push(response.status());
});
const settings = async () => {
  await page.getByText('Plugins', { exact: true }).first().click();
  await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
  return page.getByRole('switch', { name: /Enable.*skillhub/i }).first();
};
const checked = async (toggle, value) => {
  const deadline = Date.now() + 15000;
  while (await toggle.getAttribute('aria-checked') !== String(value)) {
    assert(Date.now() < deadline, 'Bundle enablement did not settle');
    await delay(50);
  }
};
let disabled = false;
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: /document-helper/ }).waitFor();
  for (let cycle = 0; cycle < 2; cycle++) {
    const toggle = await settings();
    await checked(toggle, true);
    await toggle.click(); disabled = true;
    await checked(toggle, false);
    await page.getByText('SkillHub', { exact: true }).waitFor({ state: 'hidden' });
    assert.equal(await plaza.count(), 0);
    assert.equal(await page.getByText(/^Could not disable:/).count(), 0, 'Bundle disable must not report a failure');
    await page.screenshot({ path: `${output}/bundle-disabled-${cycle}.png` });
    await toggle.click();
    await checked(toggle, true); disabled = false;
    await page.getByText('SkillHub', { exact: true }).waitFor();
    assert.equal(await page.getByText('SkillHub', { exact: true }).count(), 1);
    await page.getByText('SkillHub', { exact: true }).click();
    await plaza.getByRole('button', { name: /document-helper/ }).waitFor();
    await plaza.getByLabel('搜索技能').fill('nothing');
    await plaza.getByRole('button', { name: '搜索', exact: true }).click();
    await plaza.getByText('没有找到匹配的技能，请尝试其他关键词。', { exact: true }).waitFor();
    await plaza.getByRole('button', { name: '清空', exact: true }).click();
    await plaza.getByRole('button', { name: /document-helper/ }).waitFor();
  }
  assert.deepEqual(changes, [200, 200, 200, 200]);
  assert.deepEqual(errors, []);
  const result = { passed: true, cycles: 2, nativeBundleToggle: true, sidebarRemoved: true,
    oneSidebarAfterEnable: true, searchAfterEnable: true, changes, errors };
  await page.screenshot({ path: `${output}/bundle-restored.png` });
  await writeFile(`${output}/bundle-lifecycle-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/bundle-lifecycle-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), changes, errors }));
  process.exitCode = 1;
} finally {
  if (disabled) {
    try { const toggle = await settings(); if (await toggle.getAttribute('aria-checked') === 'false') { await toggle.click(); await checked(toggle, true); } }
    catch { console.log('Isolated bundle restoration needs verification'); process.exitCode = 1; }
  }
  await browser.close();
}
