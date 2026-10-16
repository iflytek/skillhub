import { selectValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { readFile, writeFile, rename, stat } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { dirname, join, resolve, sep } from 'node:path';
import { tmpdir } from 'node:os';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title,
  DSH_INSTALL_FIXTURE_WORKSPACE: workspace, DSH_INSTALL_FIXTURE_PROJECT: project } = process.env;
assert(entry && output && title && workspace && project);
assert(resolve(project).startsWith(resolve(tmpdir()) + sep));
const target = join(project, '.dsh/skills/document-helper');
const backup = join(dirname(project), 'stale-record-backup-' + randomUUID());
const content = await readFile(join(target, 'references/note.md'), 'utf8');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
let writes = 0;
let moved = false;
let submitted = false;
page.on('request', request => { if (/\/(startInstall|executePlan|recoverOperation)$/.test(new URL(request.url()).pathname)) writes++; });
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  if (!await page.getByText(title, { exact: true }).waitFor({ timeout: 3000 }).then(() => true, () => false)) {
    await page.getByText(workspace, { exact: true }).click();
  }
  await page.getByText(title, { exact: true }).click();
  await rename(target, backup); moved = true;
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
  const installed = plaza.getByRole('region', { name: '已安装技能', exact: true });
  await selectValue(installed.getByRole('combobox', { name: '筛选安装范围' }), 'project');
  await installed.getByText('安装目录缺失', { exact: true }).waitFor();
  await installed.getByRole('button', { name: '清理失效记录', exact: true }).click();
  const detail = plaza.getByRole('region', { name: '已安装技能详情' });
  const prepare = detail.getByRole('button', { name: '清理失效记录', exact: true });
  await prepare.click();
  const dialog = page.getByRole('dialog', { name: '确认清理失效记录' });
  await dialog.waitFor();
  await dialog.getByText('此位置的目录已缺失，本次只移除安装记录，不删除文件。', { exact: true }).waitFor();
  const cancel = dialog.getByRole('button', { name: '取消', exact: true });
  assert(await cancel.evaluate(button => button === document.activeElement));
  const confirm = dialog.getByRole('button', { name: '仅清理失效记录', exact: true });
  await page.keyboard.press('Tab');
  assert(await confirm.evaluate(button => button === document.activeElement));
  await page.keyboard.press('Shift+Tab');
  assert(await cancel.evaluate(button => button === document.activeElement));
  await page.keyboard.press('Escape');
  await dialog.waitFor({ state: 'hidden' });
  assert.equal(writes, 0);
  assert(await prepare.evaluate(button => button === document.activeElement));
  await page.keyboard.press('Enter');
  await dialog.waitFor();
  assert(await cancel.evaluate(button => button === document.activeElement));
  await page.screenshot({ path: join(output, 'stale-record-confirmation.png') });
  await cancel.click();assert.equal(writes, 0);
  assert(await prepare.evaluate(button => button === document.activeElement));
  await prepare.click();
  submitted = true; // On uncertain transport, retain the fixture backup rather than recreate an untracked target.
  await dialog.getByRole('button', { name: '仅清理失效记录', exact: true }).click();
  await detail.getByText('失效安装记录已清理。', { exact: true }).waitFor();
  assert.equal(writes, 1);
  assert.equal(await readFile(join(backup, 'references/note.md'), 'utf8'), content);
  await assert.rejects(stat(target), { code: 'ENOENT' });
  await plaza.getByRole('button', { name: '← 返回已安装', exact: true }).click();
  assert.equal(await installed.getByRole('button', { name: '查看已安装详情', exact: true }).count(), 0);
  await writeFile(join(output, 'stale-record-browser-result.json'), JSON.stringify({ passed: true,
    listAndDetailUseCleanupLabel:true,confirmationExplainsNoFileDeletion:true,cancelFocused:true,
    keyboardActionsReachable:true,escapeCancels:true,focusRestored:true,
    cancelWrites:0,confirmedWrites:writes,backupPreserved:true,targetStillAbsent:true,recordNoLongerListed:true,modelInvoked:false }, null, 2));
} finally {
  if (moved && !submitted) await rename(backup, target);
  await browser.close();
}
