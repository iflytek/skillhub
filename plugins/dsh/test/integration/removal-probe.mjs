import { revealReceipt } from './receipt-disclosure.mjs';
import { selectValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium } from 'playwright';

const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title,
  DSH_INSTALL_FIXTURE_WORKSPACE: workspace } = process.env;
if (!entry || !output || !title || !workspace) throw new Error('Set isolated Host, output and fixture session');
const stale = process.env.DSH_REMOVAL_KIND === 'stale';
const recoveryFixture = process.env.DSH_REMOVAL_RECOVERY_FIXTURE === '1';
const responseLoss = !recoveryFixture && process.env.DSH_REMOVAL_RESPONSE_LOSS !== '0';
const restart = process.env.DSH_PROBE_RESTART === '1';
const narrow = process.env.DSH_PROBE_NARROW === '1';
const offline = process.env.DSH_REMOVAL_OFFLINE === '1';
const permissionDenied = process.env.DSH_REMOVAL_PERMISSION_DENIED === '1';
const cleanupFailure = process.env.DSH_REMOVAL_CLEANUP_FAILURE === '1';
const success = stale ? '失效安装记录已清理。' : '此安装位置已卸载。';
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
let submissions = 0;
let acceptedId;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/executePlan')) submissions++; });
async function run() {
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  if (!await page.getByText(title, { exact: true }).isVisible()) await page.getByText(workspace, { exact: true }).click();
  await page.getByText(title, { exact: true }).click();
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
  const installed = plaza.getByRole('region', { name: '已安装技能' });
  await selectValue(installed.getByRole('combobox', { name: '筛选安装范围' }), 'project');
  await installed.getByRole('button', { name: '查看已安装详情', exact: true }).click();
  const detail = plaza.getByRole('region', { name: '已安装技能详情' });
  if (offline) {
    await detail.getByText('远端详情暂时不可用，以上保留本地安装信息。未切换到其他版本。', { exact: true }).waitFor();
    await detail.getByText('@demo/document-helper · 已安装版本 1.2.0', { exact: true }).waitFor();
  }
  await detail.getByRole('button', { name: '卸载此安装位置', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: stale ? '确认清理失效记录' : '确认卸载技能', exact: true });
  await dialog.waitFor();
  if (narrow) await page.setViewportSize({ width: 390, height: 844 });
  assert(await dialog.evaluate(element => {
    const box = element.getBoundingClientRect();
    return box.left >= 0 && box.right <= window.innerWidth && element.scrollWidth <= element.clientWidth;
  }), 'Confirmation must fit the viewport without horizontal overflow');
  assert(await dialog.getByRole('button', { name: '取消', exact: true }).evaluate(element => element === document.activeElement));
  await page.screenshot({ path: `${output}/removal-confirmation.png` });
  await dialog.getByRole('button', { name: '取消', exact: true }).click();
  if (narrow) await page.setViewportSize({ width: 1440, height: 1000 });
  assert.equal(submissions, 0);
  await page.route('**/executePlan', async route => {
    const response = await route.fetch();
    const result = (await response.json()).result;
    assert.equal(result.ok, true);
    assert.equal(result.value.kind, stale ? 'remove-stale-record' : 'uninstall');
    if (!acceptedId) { acceptedId = result.value.operationId; if (responseLoss) await route.abort('failed'); else await route.fulfill({ response }); }
    else { assert.equal(result.value.operationId, acceptedId); await route.fulfill({ response }); }
  });
  await detail.getByRole('button', { name: '卸载此安装位置', exact: true }).click();
  await dialog.getByRole('button', { name: stale ? '仅清理失效记录' : '确认卸载', exact: true }).click();
  if (cleanupFailure) {
    await detail.getByText(success, { exact: true }).waitFor();
    const warning = detail.getByText('有隔离残留或操作记录需要进一步核验。', { exact: true });
    await warning.waitFor();
    await page.screenshot({ path: `${output}/removal-cleanup-warning.png` });
    await writeFile(`${output}/cleanup-ready.json`, JSON.stringify({ operationId: acceptedId }));
    const deadline = Date.now() + 60000;
    for (;;) {
      try { assert.equal(JSON.parse(await readFile(`${output}/cleanup-resume.json`, 'utf8')).restored, true); break; }
      catch (error) { if (error.code !== 'ENOENT') throw error; }
      assert(Date.now() < deadline, 'Cleanup permissions were not restored');
      await delay(100);
    }
    let recoveries = 0;
    page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/recoverOperation')) recoveries++; });
    await detail.getByRole('button', { name: '核验并恢复', exact: true }).click();
    await warning.waitFor({ state: 'hidden' });
    await detail.getByText(success, { exact: true }).waitFor();
    assert.equal(recoveries, 1);
  }
  if (permissionDenied) {
    await detail.getByText('卸载未完成。无需恢复文件。', { exact: true }).waitFor();
    assert.equal(await detail.getByText(success, { exact: true }).count(), 0);
    await page.screenshot({ path: `${output}/removal-permission-denied.png` });
    await detail.getByRole('button', { name: '关闭本次卸载结果', exact: true }).click();
    await detail.getByRole('button', { name: '← 返回已安装', exact: true }).click();
    await installed.getByText('当前显示 1 项安装记录。', { exact: true }).waitFor();
    assert.equal(submissions, 1);
    assert.deepEqual(errors, []);
    const result = { passed: true, permissionDenied: true, failureNotReportedAsSuccess: true,
      installedRowPreserved: true, submissions, operationId: acceptedId, errors };
    await writeFile(`${output}/removal-result.json`, JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result));
    return;
  }
  if (recoveryFixture) {
    await detail.getByRole('button', { name: '核验并恢复', exact: true }).waitFor();
    let recoveryRegion = detail;
    if (restart) {
      await writeFile(`${output}/restart-ready.json`, JSON.stringify({ operationId: acceptedId }));
      const deadline = Date.now() + 120000;
      let resumed;
      while (Date.now() < deadline) {
        try { resumed = JSON.parse(await readFile(`${output}/restart-resume.json`, 'utf8')); break; }
        catch (error) { if (error.code !== 'ENOENT') throw error; }
        await delay(200);
      }
      assert(resumed?.entry, 'Host restart did not complete');
      assert.equal(new URL(resumed.entry).origin, new URL(entry).origin, 'Preserve origin for sessionStorage');
      await page.goto(resumed.entry, { waitUntil: 'domcontentloaded' });
      await page.getByText('技能中心', { exact: true }).click();
      await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
      recoveryRegion = installed.getByRole('region', { name: '未关闭的卸载结果', exact: true });
      await recoveryRegion.getByRole('button', { name: '核验并恢复', exact: true }).waitFor();
    }
    let recoveries = 0;
    await page.route('**/recoverOperation', async route => {
      recoveries++;
      const response = await route.fetch();
      const result = (await response.json()).result;
      assert.equal(result.ok, true);
      assert.equal(result.value.operationId, acceptedId);
      assert.equal(result.value.recovery, 'restored');
      // Lose the first recovery reply after the Host has persisted restoration.
      if (recoveries === 1) await route.abort('failed'); else await route.fulfill({ response });
    });
    await recoveryRegion.getByRole('button', { name: '核验并恢复', exact: true }).click();
    await recoveryRegion.getByText('卸载未完成。原文件已恢复。', { exact: true }).waitFor();
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.getByText('技能中心', { exact: true }).click();
    await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
    const history = installed.getByRole('region', { name: '未关闭的卸载结果', exact: true });
    await history.getByText('卸载未完成。原文件已恢复。', { exact: true }).waitFor();
    assert.equal(submissions, 1);
    assert.equal(recoveries, 1);
    assert.deepEqual(errors, []);
    await page.screenshot({ path: `${output}/removal-restored.png` });
    await writeFile(`${output}/removal-result.json`, JSON.stringify({ passed: true, recoveryVerified: true,
      recoveryResponseLossVerified: true, reloadRecoveryVerified: true, hostRestartVerified: restart, submissions, recoveries, operationId: acceptedId, errors }, null, 2));
    console.log('Removal recovery browser verification passed');
    return;
  }
  if (responseLoss) {
    await detail.getByRole('button', { name: '查询原卸载请求', exact: true }).waitFor();
    await page.reload({ waitUntil: 'domcontentloaded' });
    const later = page.getByRole('button', { name: 'Configure later', exact: true });
    if (await later.isVisible()) await later.click();
    await page.getByText('技能中心', { exact: true }).click();
    await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
    const history = installed.getByRole('region', { name: '未关闭的卸载结果', exact: true });
    await history.getByRole('button', { name: '查询原卸载请求', exact: true }).click();
    await revealReceipt(history.getByText(success, { exact: true }));
    await selectValue(installed.getByRole('combobox', { name: '筛选安装范围' }), 'project');
    await installed.getByText('当前显示 0 项安装记录。', { exact: true }).waitFor();
    await page.screenshot({ path: `${output}/removal-recovered.png` });
    await history.getByRole('button', { name: '关闭本次卸载结果', exact: true }).click();
    await history.waitFor({ state: 'hidden' });
  } else {
    await detail.getByText(success, { exact: true }).waitFor();
    await detail.getByText('此安装位置的操作已完成，请查看下方结果或返回列表。', { exact: true }).waitFor();
    await page.screenshot({ path: `${output}/removal-recovered.png` });
    await detail.getByRole('button', { name: '关闭本次卸载结果', exact: true }).click();
    assert(await detail.getByRole('heading', { name: 'document-helper', exact: true }).evaluate(element => element === document.activeElement));
    await detail.getByRole('button', { name: '← 返回已安装', exact: true }).click();
    await installed.getByText('当前显示 0 项安装记录。', { exact: true }).waitFor();
  }
  assert(await installed.getByRole('combobox', { name: '筛选安装范围' }).evaluate(element => element === document.activeElement));
  assert.equal(submissions, responseLoss ? 2 : 1);
  assert.deepEqual(errors, []);
  const result = { passed: true, cancellationVerified: true, cancelFocused: true, responseLossRecovered: responseLoss,
    staleRecordCleanup: stale, narrowConfirmationVerified: narrow, finalFocusVerified: true, directDetailFocusVerified: !responseLoss,
    offlineLocalDetailVerified: offline, cleanupFailureRecovered: cleanupFailure,
    installedRowRemoved: true, resultClosed: true, submissions, operationId: acceptedId, errors };
  await writeFile(`${output}/removal-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/removal-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await browser.close(); }

}
await run();
