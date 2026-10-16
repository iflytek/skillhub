import { selectValue, selectedValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_FIXTURE_REQUEST_LOG: log } = process.env;
if (!entry || !output || !log) throw new Error('Set isolated Host, output and Registry request log');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const errors = [];
const results = [];
const liveSwitch = process.env.DSH_PROBE_LIVE_CONFIG === '1';
const moduleStatus = Number(process.env.DSH_FIXTURE_MODULE_STATUS ?? 200);
const emptyModules = process.env.DSH_FIXTURE_EMPTY_MODULES === '1';
assert.ok([200, 403, 404, 503].includes(moduleStatus));
const moduleError = moduleStatus === 403 ? '当前来源不允许匿名读取此项信息。'
  : moduleStatus === 404 ? '未找到此项信息，无法判断是内容不存在还是来源尚未支持。'
    : '此项信息暂时无法读取，请重试；若来源或配置已变化，请重新打开详情。';
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
const requests = async () => (await readFile(log, 'utf8').catch(error => { if (error.code === 'ENOENT') return ''; throw error; })).trim().split('\n').filter(Boolean).map(line => JSON.parse(line));
async function settings() {
  await page.getByText('Plugins', { exact: true }).click();
  await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
  const form = page.getByRole('form', { name: 'SkillHub 配置' });
  await form.waitFor();
  return form;
}
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  for (const mode of liveSwitch ? ['show', 'hide', 'auto'] : ['hide', 'show', 'auto']) {
    const form = await settings();
    for (let index = 0; index < 3; index++) await selectValue(form.getByRole('combobox').nth(index), mode);
    await form.getByRole('button', { name: '保存配置', exact: true }).click();
    await form.getByText('配置已保存。', { exact: true }).waitFor();
    if (!liveSwitch) {
      await page.reload({ waitUntil: 'domcontentloaded' });
      await page.getByRole('button', { name: 'Configure later', exact: true }).click();
    }
    const restored = liveSwitch ? form : await settings();
    for (let index = 0; index < 3; index++) assert.equal(await selectedValue(restored.getByRole('combobox').nth(index)), mode);
    const before = (await requests()).length;
    await page.getByText('技能中心', { exact: true }).click();
    const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
    const card = plaza.getByRole('button', { name: /document-helper/ });
    const detailTab = plaza.getByRole('tab', { name: '详情', exact: true });
    await card.or(detailTab).first().waitFor();
    if (await card.count()) await card.click();
    await plaza.getByRole('tab', { name: '详情', exact: true }).waitFor();
    const optional = (await requests()).slice(before).filter(item => /\/(versions|reviews)$/.test(item.path));
    if (mode === 'hide') {
      for (const name of ['版本', '安全审核', '评论']) assert.equal(await plaza.getByRole('tab', { name, exact: true }).count(), 0);
      assert.deepEqual(optional, []);
    } else if (mode === 'auto' && moduleStatus !== 200) {
      assert.equal(optional.length, 2);
      assert.ok(optional.every(item => item.size === '1'));
      for (const name of ['版本', '安全审核', '评论']) assert.equal(await plaza.getByRole('tab', { name, exact: true }).count(), 0);
      if (moduleStatus !== 403) await plaza.getByText('版本、评论是否可读取尚未确认，不代表没有内容。', { exact: true }).waitFor();
      assert.equal((await requests()).slice(before).filter(item => item.size === '20').length, 0);
    } else {
      assert.equal(optional.length, mode === 'auto' ? 2 : 0);
      assert.ok(optional.every(item => item.size === '1'));
      await plaza.getByRole('tab', { name: '版本', exact: true }).click();
      await plaza.getByText(moduleStatus === 200 ? emptyModules ? '暂无版本信息。' : '改进文档提取' : moduleError, { exact: true }).waitFor();
      await plaza.getByRole('tab', { name: '评论', exact: true }).click();
      if (moduleStatus === 200) await plaza.getByText('评论针对整个技能，不限定当前版本；不代表安全审核结论。', { exact: true }).waitFor();
      await plaza.getByText(moduleStatus === 200 ? emptyModules ? '暂无评论信息。' : '适合整理长文档' : moduleError, { exact: true }).waitFor();
      assert.equal(await plaza.getByText('暂无评论信息。', { exact: true }).count(), emptyModules && moduleStatus === 200 ? 1 : 0);
      if (emptyModules && moduleStatus === 200) {
        assert.equal(await plaza.getByRole('button', { name: '上一页', exact: true }).isDisabled(), true);
        assert.equal(await plaza.getByRole('button', { name: '下一页', exact: true }).isDisabled(), true);
      }
      if (mode === 'show') {
        await plaza.getByRole('tab', { name: '安全审核', exact: true }).click();
        await plaza.getByText('当前来源的审核信息不支持匿名读取。', { exact: true }).waitFor();
      } else assert.equal(await plaza.getByRole('tab', { name: '安全审核', exact: true }).count(), 0);
      const bodies = (await requests()).slice(before).filter(item => item.size === '20');
      assert.equal(bodies.length, 2);
    }
    assert.equal((await requests()).some(item => item.path.includes('security-audit')), false);
    await page.screenshot({ path: `${output}/tabs-${mode}.png` });
    results.push({ mode, persistedAfterReload: !liveSwitch, appliedWithoutReload: liveSwitch, probeRequests: optional.length, passed: true });
  }
  assert.deepEqual(errors, []);
  const result = { passed: true, moduleStatus, emptyModules, results, errors };
  await writeFile(`${output}/tab-config-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/tabs-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await browser.close(); }
