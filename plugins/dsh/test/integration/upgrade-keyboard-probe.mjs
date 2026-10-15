import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { tmpdir } from 'node:os';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title,
  DSH_INSTALL_FIXTURE_WORKSPACE: workspace, DSH_INSTALL_FIXTURE_PROJECT: project } = process.env;
assert(entry && output && title && workspace && project);
assert(resolve(project).startsWith(resolve(tmpdir()) + sep));
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
let writes = 0;
page.on('request', request => { if (/\/(startInstall|executePlan|recoverOperation)$/.test(new URL(request.url()).pathname)) writes++; });
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  if (!await page.getByText(title, { exact: true }).waitFor({ timeout: 3000 }).then(() => true, () => false)) {
    await page.getByText(workspace, { exact: true }).click();
  }
  await page.getByText(title, { exact: true }).click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: '已安装', exact: true }).click();
  const installed = plaza.getByRole('region', { name: '已安装技能', exact: true });
  await installed.getByRole('combobox', { name: '筛选安装范围' }).selectOption('project');
  await installed.getByRole('button', { name: '查看已安装详情', exact: true }).click();
  const detail = plaza.getByRole('region', { name: '已安装技能详情' });
  const remove = detail.getByRole('button', { name: '卸载此安装位置', exact: true });
  await remove.waitFor();
  await page.waitForFunction(() => [...document.querySelectorAll('button')].some(button => button.textContent === '卸载此安装位置' && !button.disabled));
  assert(process.env.DSH_FIXTURE_UPDATE_STATE);
  await writeFile(process.env.DSH_FIXTURE_UPDATE_STATE, 'newer');
  await detail.getByRole('button', { name: '检查更新', exact: true }).click();
  const upgrade = detail.getByRole('button', { name: '生成升级计划', exact: true });
  await upgrade.waitFor();
  assert(await upgrade.isEnabled());
  await upgrade.focus();
  await page.keyboard.press('Enter');
  const cancel = detail.getByRole('button', { name: '取消', exact: true });
  await cancel.waitFor();
  assert(await cancel.evaluate(button => button === document.activeElement), 'Prepared plan must move focus to cancellation');
  await page.keyboard.press('Enter');
  await upgrade.waitFor();
  assert(await upgrade.evaluate(button => button === document.activeElement), 'Cancel must restore the plan trigger');
  assert.equal(writes, 0);
  await writeFile(join(output, 'upgrade-keyboard-result.json'), JSON.stringify({ passed: true,
    keyboardPrepare:true,cancelFocused:true,cancelRestoresTrigger:true,writeRequests:writes,modelInvoked:false }, null, 2));
} finally { if (process.env.DSH_FIXTURE_UPDATE_STATE) await writeFile(process.env.DSH_FIXTURE_UPDATE_STATE, 'current'); await browser.close(); }
