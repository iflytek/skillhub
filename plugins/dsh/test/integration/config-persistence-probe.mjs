import { selectValue, selectedValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_FIXTURE_REQUEST_LOG: log,
  DSH_CONFIG_PHASE: phase } = process.env;
if (!entry || !output || !log || !['save', 'verify'].includes(phase)) throw new Error('Set isolated Host, output, request log and save/verify phase');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const errors = [];
const modes = ['hide', 'show', 'auto'];
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
const requests = async () => (await readFile(log, 'utf8').catch(error => {
  if (error.code === 'ENOENT') return ''; throw error;
})).trim().split('\n').filter(Boolean).map(line => JSON.parse(line));
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  assert.equal(await page.evaluate(() => Object.keys(sessionStorage).filter(key => key.startsWith('skillhub:')).length), 0);
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  await page.getByText('Plugins', { exact: true }).click();
  await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
  const form = page.getByRole('form', { name: 'SkillHub 配置' });
  await form.waitFor();
  if (phase === 'save') {
    for (let index = 0; index < modes.length; index++) await selectValue(form.getByRole('combobox').nth(index), modes[index]);
    await form.getByRole('button', { name: '保存配置', exact: true }).click();
    await form.getByText('配置已保存。', { exact: true }).waitFor();
  }
  for (let index = 0; index < modes.length; index++) assert.equal(await selectedValue(form.getByRole('combobox').nth(index)), modes[index]);
  if (phase === 'verify') {
    const before = (await requests()).length;
    await page.getByText('技能中心', { exact: true }).click();
    const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
    await plaza.getByRole('button', { name: /document-helper/ }).click();
    await plaza.getByRole('tab', { name: '详情', exact: true }).waitFor();
    assert.equal(await plaza.getByRole('tab', { name: '版本', exact: true }).count(), 0);
    const probes = (await requests()).slice(before).filter(item => /\/(versions|reviews)$/.test(item.path));
    assert.deepEqual(probes, [{ path: '/api/v1/skills/42/reviews', page: '0', size: '1' }]);
    await plaza.getByRole('tab', { name: '安全审核', exact: true }).click();
    await plaza.getByText('当前来源的审核信息不支持匿名读取。', { exact: true }).waitFor();
    await plaza.getByRole('tab', { name: '评论', exact: true }).click();
    await plaza.getByText('适合整理长文档', { exact: true }).waitFor();
    const reads = (await requests()).slice(before);
    assert.equal(reads.some(item => item.path.endsWith('/versions') || item.path.includes('security-audit')), false);
    assert.equal(reads.filter(item => item.path.endsWith('/reviews') && item.size === '20').length, 1);
  }
  assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/config-${phase}.png` });
  const result = { passed: true, phase, modes, freshBrowser: true, errors };
  await writeFile(`${output}/config-${phase}-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/config-${phase}-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await browser.close(); }
