import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host, output and fixture title');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const installHistory = process.env.DSH_HISTORY_KIND === 'install';
const errors = []; const actions = [];
page.on('pageerror', error => errors.push(error.message));
page.on('request', request => {
  const method = new URL(request.url()).pathname.split('/').at(-1);
  if (['openConversationDetail', 'reopenConversationDetail', 'acknowledgeConversationDetail', 'getOperation', 'startInstall', 'executePlan', 'recoverOperation'].includes(method)) actions.push(method);
});
async function expand() {
  const turn = page.locator('button[data-turn-process]:enabled').last();
  if (await turn.getAttribute('aria-expanded') === 'false') await turn.click();
  const fallback = page.getByRole('region', { name: installHistory ? 'SkillHub 安装记录' : 'SkillHub 搜索记录', exact: true });
  if (!await fallback.isVisible()) await page.getByText(/^(Called tools|调用的工具|已调用工具)$/).click();
  await fallback.getByText(installHistory ? '这条记录的格式无法识别，不能核验安装结果。' : '这条记录缺少可识别的搜索结果，暂时无法展示卡片。', { exact: true }).waitFor();
  assert.equal(await fallback.getByRole('button', { name: '查看技能详情', exact: true }).count(), 0);
  assert.equal(await page.getByRole('region', { name: installHistory ? 'SkillHub 安装结果' : 'SkillHub 搜索结果', exact: true }).count(), 0);
}
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const later = page.getByRole('button', { name: /^(Configure later|稍后配置)$/ });
  await page.addLocatorHandler(later, async locator => { await locator.click(); });
  await page.getByText(title, { exact: true }).click();
  await expand();
  await page.reload({ waitUntil: 'domcontentloaded' });
  await expand();
  assert.deepEqual(actions, []); assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/unknown-history.png` });
  const result = { passed: true, kind: installHistory ? 'install' : 'search', unknownSchemaRejected: true, validBodyNotUsedAsMetadata: true,
    persistedReload: true, actions, modelInvoked: false };
  await writeFile(`${output}/unknown-history-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/unknown-history-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.split('Call log:')[0], actions, pageErrorCount: errors.length }));
  process.exitCode = 1;
} finally { await browser.close(); }
