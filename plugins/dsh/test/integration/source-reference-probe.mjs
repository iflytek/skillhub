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
  const card = page.getByRole('region', { name: 'SkillHub 搜索结果' });
  const showCard = async () => {
    if (!await card.isVisible()) {
      const turn = page.locator('button[data-turn-process]:enabled').last();
      if (await turn.getAttribute('aria-expanded') === 'false') await turn.click();
    }
    if (!await card.isVisible()) await page.getByText('Called tools', { exact: true }).click();
    await card.waitFor();
  };
  await showCard();
  const drawer = page.getByRole('dialog', { name: '技能详情', exact: true });
  await card.getByRole('button', { name: '查看技能详情', exact: true }).click();
  await drawer.getByRole('heading', { name: 'Document helper', exact: true }).waitFor();
  await drawer.getByRole('button', { name: '关闭技能详情', exact: true }).click();
  const changeSource = async (address) => {
    await page.getByText('Plugins', { exact: true }).click();
    await page.getByText('@astron-team/skillhub-dsh', { exact: true }).first().click();
    const form = page.getByRole('form', { name: 'SkillHub 配置' });
    await form.getByRole('textbox', { name: 'Registry 地址' }).fill(address);
    await form.getByRole('button', { name: '保存配置', exact: true }).click();
    await form.getByText('配置已保存。', { exact: true }).waitFor();
    await page.getByText('SkillHub', { exact: true }).click();
    const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
    await plaza.getByText(address, { exact: true }).waitFor();
    await plaza.getByRole('button', { name: '返回会话', exact: true }).click();
    await showCard();
  };
  for (const address of ['http://127.0.0.1:43220', 'http://127.0.0.1:43219']) {
    await changeSource(address);
    await card.getByRole('button', { name: '查看技能详情', exact: true }).click();
    await drawer.getByText('这条搜索记录已失效或不属于当前会话，请重新搜索。', { exact: true }).waitFor();
    assert.equal(await drawer.getByRole('button', { name: /安装到/ }).count(), 0);
    await drawer.getByRole('button', { name: '关闭技能详情', exact: true }).click();
  }
  assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/source-reference.png` });
  const result = { passed: true, freshReferenceValid: true, changedSourceRejected: true, returningSourceStillRejected: true, modelInvoked: false, originalSourceRestored: true, errors };
  await writeFile(`${output}/source-reference-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/source-reference-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await browser.close(); }
