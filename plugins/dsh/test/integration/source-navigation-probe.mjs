import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_SEARCH_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host URL, output and session title');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 700 } });
const errors = [];
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  const sessionVisible = await page.getByText(title, { exact: true }).waitFor({ state: 'visible', timeout: 3000 }).then(() => true, () => false);
  if (!sessionVisible) {
    const workspace = process.env.DSH_SEARCH_FIXTURE_WORKSPACE;
    if (!workspace) throw new Error('Set fixture workspace to expand its session');
    await page.getByText(workspace, { exact: true }).click();
  }
  await page.getByText(title, { exact: true }).click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: '已安装', exact: true }).click();
  const scope = plaza.getByRole('combobox', { name: '筛选安装范围' });
  const keyword = plaza.getByRole('searchbox', { name: '过滤已安装技能' });
  await scope.selectOption('project');
  await keyword.fill('document-helper');
  await plaza.getByRole('button', { name: '查看已安装详情', exact: true }).click();
  await plaza.getByRole('region', { name: '已安装技能详情', exact: true }).waitFor();
  const originalSource = 'http://127.0.0.1:43219';
  const otherSource = 'http://127.0.0.1:43220';
  const source = async (address) => {
    await page.getByText('Plugins', { exact: true }).click();
    await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
    const form = page.getByRole('form', { name: 'SkillHub 配置' });
    await form.getByRole('textbox', { name: 'Registry 地址' }).fill(address);
    await form.getByRole('button', { name: '保存配置', exact: true }).click();
    await form.getByText('配置已保存。', { exact: true }).waitFor();
    await page.getByText('SkillHub', { exact: true }).click();
    await plaza.getByText(address, { exact: true }).waitFor();
  };
  await source(otherSource);
  await plaza.getByRole('button', { name: '已安装', exact: true }).click();
  await scope.waitFor();
  assert.equal(await scope.inputValue(), 'all');
  assert.equal(await keyword.inputValue(), '');
  await plaza.getByText('当前显示 0 项安装记录。', { exact: true }).waitFor();
  assert.equal(await plaza.getByRole('region', { name: '已安装技能详情', exact: true }).count(), 0);
  await plaza.getByRole('button', { name: '浏览技能', exact: true }).click();
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  await plaza.getByRole('heading', { name: '文档助手', exact: true }).waitFor();
  await source(originalSource);
  // Old detail must not silently become a detail from the new source.
  const stale = plaza.getByText('来源已变化，请返回技能广场重新选择。', { exact: true });
  const browse = plaza.getByRole('button', { name: '浏览技能', exact: true });
  await stale.or(browse).first().waitFor();
  if (await stale.isVisible()) await plaza.getByRole('button', { name: '← 返回技能广场', exact: true }).click();
  await plaza.getByRole('button', { name: '已安装', exact: true }).click();
  await scope.selectOption('project');
  await plaza.getByText('当前显示 1 项安装记录。', { exact: true }).waitFor();
  assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/source-navigation.png` });
  const result = { passed: true, sourceReset: true, sourceLocalInventory: true, staleDetailRejected: true, originalSourceRestored: true, errors };
  await writeFile(`${output}/source-navigation-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/source-navigation-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await browser.close(); }
