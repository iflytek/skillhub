import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title,
  DSH_INSTALL_FIXTURE_WORKSPACE: workspace, DSH_FIXTURE_UPDATE_STATE: state } = process.env;
if (!entry || !output || !title || !workspace || !state) throw new Error('Set isolated Host and fixture session');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
let blocked = true;
let writes = 0;
const errors = [];
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => {
  if (/\/(startInstall|executePlan|recoverOperation)$/.test(new URL(request.url()).pathname)) writes++;
});
try {
  // Fault injection tests the UI only. Host dependency validation is tested separately.
  await page.route('**/getCompatibility', async route => {
    const response = await route.fetch();
    const body = await response.json();
    assert.equal(body.result.ok, true);
    assert.equal(body.result.value.dependencyBaseline, 'matched');
    if (blocked) body.result.value = { dependencyBaseline: 'unverified', candidateDshVersion: '0.2.0-rc.2', localWritesAllowed: false };
    await route.fulfill({ response, json: body });
  });
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  if (!await page.getByText(title, { exact: true }).isVisible()) await page.getByText(workspace, { exact: true }).click();
  await page.getByText(title, { exact: true }).click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  const install = plaza.getByRole('region', { name: '安装技能' });
  await install.getByRole('button', { name: '重新核验写入兼容性', exact: true }).waitFor();
  assert(await install.getByRole('button', { name: '安装到当前用户', exact: true }).isDisabled());
  blocked = false;
  await install.getByRole('button', { name: '重新核验写入兼容性', exact: true }).click();
  await install.getByRole('button', { name: '重新核验写入兼容性', exact: true }).waitFor({ state: 'hidden' });
  assert(await install.getByRole('button', { name: '安装到当前用户', exact: true }).isEnabled());
  await plaza.getByRole('button', { name: '← 返回技能广场', exact: true }).click();
  blocked = true;
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.getByText('SkillHub', { exact: true }).click();
  await plaza.getByRole('button', { name: '已安装', exact: true }).click();
  const installed = plaza.getByRole('region', { name: '已安装技能', exact: true });
  await installed.getByRole('combobox', { name: '筛选安装范围' }).selectOption('project');
  await installed.getByRole('button', { name: '查看已安装详情', exact: true }).click();
  const detail = plaza.getByRole('region', { name: '已安装技能详情' });
  await detail.getByRole('button', { name: '卸载此安装位置', exact: true }).waitFor();
  assert(await detail.getByRole('button', { name: '卸载此安装位置', exact: true }).isDisabled());
  await writeFile(state, 'newer');
  await detail.getByRole('button', { name: '检查更新', exact: true }).click();
  await detail.getByRole('button', { name: '生成升级计划', exact: true }).waitFor();
  assert(await detail.getByRole('button', { name: '生成升级计划', exact: true }).isDisabled());
  await page.screenshot({ path: `${output}/compatibility-disabled.png` });
  blocked = false;
  await detail.getByRole('button', { name: '重新核验写入兼容性', exact: true }).first().click();
  await detail.getByRole('button', { name: '重新核验写入兼容性', exact: true }).first().waitFor({ state: 'hidden' });
  assert(await detail.getByRole('button', { name: '卸载此安装位置', exact: true }).isEnabled());
  assert(await detail.getByRole('button', { name: '生成升级计划', exact: true }).isEnabled());
  assert.equal(writes, 0);
  assert.deepEqual(errors, []);
  const result = { passed: true, injectedClientCompatibility: true, actualHostBaselineMatched: true,
    installDisabled: true, upgradeDisabled: true, uninstallDisabled: true, readOnlyCheckAvailable: true,
    explicitRetryEnabledControls: true, writes, errors };
  await writeFile(`${output}/compatibility-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/compatibility-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await writeFile(state, 'current'); await browser.close(); }
