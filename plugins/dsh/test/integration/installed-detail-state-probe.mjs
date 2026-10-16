import { selectValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { join, resolve, sep } from 'node:path';
import { tmpdir } from 'node:os';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title,
  DSH_INSTALL_FIXTURE_WORKSPACE: workspace, DSH_INSTALL_FIXTURE_PROJECT: project } = process.env;
assert(entry && output && title && workspace && project);
assert(resolve(project).startsWith(resolve(tmpdir()) + sep));
const metadataPath = join(project, '.dsh/skills/document-helper/.skillhub/metadata.json');
const metadata = await readFile(metadataPath, 'utf8');
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
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
  const installed = plaza.getByRole('region', { name: '已安装技能', exact: true });
  await selectValue(installed.getByRole('combobox', { name: '筛选安装范围' }), 'project');
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
  await writeFile(metadataPath, '{broken');
  await detail.getByRole('button', { name: '重新读取详情', exact: true }).click();
  await detail.getByText('当前项目 · 安装记录无法识别', { exact: true }).waitFor();
  await page.screenshot({ path: join(output, 'installed-detail-invalid.png') });
  assert.equal(await remove.count(), 0, 'Fresh invalid detail must remove the action even if the selected list snapshot was present');
  assert.equal(await upgrade.count(), 0);
  await writeFile(metadataPath, metadata);
  await detail.getByRole('button', { name: '重新读取详情', exact: true }).click();
  await detail.getByText('当前项目 · 文件存在', { exact: true }).waitFor();
  assert(await remove.isEnabled());
  await upgrade.waitFor();
  await page.route('**/getInstalledDetail', route => route.abort('failed'), { times: 1 });
  await detail.getByRole('button', { name: '重新读取详情', exact: true }).click();
  await detail.getByRole('alert').filter({ hasText: /安装记录已变化|fetch|Failed/ }).waitFor();
  assert.equal(await remove.count(), 0);
  assert.equal(await upgrade.count(), 0);
  await detail.getByRole('button', { name: '重新读取详情', exact: true }).click();
  await upgrade.waitFor();
  assert(await remove.isEnabled());
  assert.equal(writes, 0);
  await writeFile(join(output, 'installed-detail-state-result.json'), JSON.stringify({ passed: true,
    staleSelectedItemDoesNotEnableRemoval: true,restoredDetailEnablesRemoval:true,invalidDetailHidesUpgrade:true,failedReadBlocksMutations:true,writeRequests:writes,modelInvoked:false }, null, 2));
} finally { await writeFile(metadataPath, metadata); if (process.env.DSH_FIXTURE_UPDATE_STATE) await writeFile(process.env.DSH_FIXTURE_UPDATE_STATE, 'current'); await browser.close(); }
