import { revealReceipt } from './receipt-disclosure.mjs';
import { selectValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { readFile, writeFile, unlink } from 'node:fs/promises';
import { setTimeout as delay } from 'node:timers/promises';
import { chromium } from 'playwright';

const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title,
  DSH_INSTALL_FIXTURE_WORKSPACE: workspace, DSH_FIXTURE_UPDATE_STATE: state } = process.env;
if (!entry || !output || !title || !workspace || !state) throw new Error('Set isolated Host, fixture and output');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'en-US' });
const expectedTargets = process.env.DSH_UPGRADE_TWO_TARGETS === '1' ? 2 : 1;
const success = process.env.DSH_UPGRADE_SUCCESS === '1';
const restart = process.env.DSH_PROBE_RESTART === '1';
const crash = process.env.DSH_UPGRADE_CRASH_FIXTURE === '1';
const expectBusy = process.env.DSH_EXPECT_RECOVERY_BUSY === '1';
assert(!crash || restart, 'Crash verification requires Host restart');
assert(!expectBusy || crash, 'Lease contention probe requires forced Host exit');
const errors = [];
let stage = 'initial-load';
const operationResponses = [];
const verifiedDetails = [];
page.on('response', async response => {
  if (!new URL(response.url()).pathname.endsWith('/getOperation')) return;
  try { const body = await response.json(); if (body.result?.ok && body.result.value) operationResponses.push(body.result.value); } catch { /* aborted navigation response */ }
});
let submissions = 0;
let recoveries = 0;
let operationId;
page.on('pageerror', error => errors.push({ stage, message: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]') }));
page.on('request', request => {
  const path = new URL(request.url()).pathname;
  if (path.endsWith('/executePlan')) submissions++;
  if (path.endsWith('/recoverOperation')) recoveries++;
});
async function assertDetails(history, outcome, afterVersion) {
  const deadline = Date.now() + 10000;
  let operation;
  while (Date.now() < deadline) {
    operation = operationResponses.findLast(item => item.operationId === operationId && item.resultDetails?.targets.every(target => target.outcome === outcome));
    if (operation) break;
    await delay(50);
  }
  assert(operation, 'Expected original operation Remote details');
  assert.equal(operation.resultDetails.beforeVersion, '1.2.0');
  assert.equal(operation.resultDetails.afterVersion, afterVersion);
  assert.equal(operation.resultDetails.targets.length, expectedTargets);
  assert(operation.resultDetails.warnings.every(warning => ['backup-cleanup', 'removal-cleanup', 'verification-required'].includes(warning)));
  assert.equal(operation.resultDetails.warnings.length, operation.warningCount);
  verifiedDetails.push({ operationId, outcome, ...operation.resultDetails });
  const details = history.locator('details').filter({ has: page.locator('summary', { hasText: '原操作结果详情' }) });
  if (await details.getAttribute('open') === null) await details.locator('summary').click();
  await details.getByText('操作前版本：1.2.0', { exact: true }).waitFor();
  await details.getByText(`操作后版本：${afterVersion ?? '未确认或不适用'}`, { exact: true }).waitFor();
  const label = { upgraded: '升级完成', unknown: '结果待核验', restored: '已恢复' }[outcome];
  assert.equal(await details.getByRole('listitem').filter({ hasText: label }).count(), expectedTargets);
  await details.screenshot({ path: `${output}/operation-details-${outcome}.png` });
}
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const welcome = page.getByRole('button', { name: 'Continue', exact: true });
  const configureLater = page.getByRole('button', { name: 'Configure later', exact: true });
  await welcome.or(configureLater).first().waitFor();
  if (await welcome.isVisible()) await welcome.click();
  await configureLater.click();
  if (!await page.getByText(title, { exact: true }).isVisible()) await page.getByText(workspace, { exact: true }).click();
  await page.getByText(title, { exact: true }).click();
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
  const installed = plaza.getByRole('region', { name: '已安装技能', exact: true });
  await selectValue(installed.getByRole('combobox', { name: '筛选安装范围' }), 'project');
  await installed.getByRole('button', { name: '查看已安装详情', exact: true }).click();
  const detail = plaza.getByRole('region', { name: '已安装技能详情' });
  await writeFile(state, 'newer');
  await detail.getByRole('button', { name: '检查更新', exact: true }).click();
  const planResponse = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/prepareUpgrade'));
  await detail.getByRole('button', { name: '生成升级计划', exact: true }).click();
  const plan = (await (await planResponse).json()).result;
  assert.equal(plan.ok, true);
  assert.equal(plan.value.status, 'ready');
  assert.equal(plan.value.targets.length, expectedTargets);
  if (expectedTargets === 2) assert.deepEqual(plan.value.targets.map(target => target.scope).sort(), ['project', 'user']);
  await page.route('**/executePlan', async route => {
    const response = await route.fetch();
    const result = (await response.json()).result;
    assert.equal(result.ok, true);
    assert.equal(result.value.kind, 'upgrade');
    operationId = result.value.operationId;
    await route.fulfill({ response });
  });
  stage = 'execute-upgrade';
  await detail.getByRole('button', { name: '确认升级', exact: true }).click();
  if (crash) {
    const deadline = Date.now() + 15000;
    while (!operationId) { assert(Date.now() < deadline, 'Upgrade acceptance unavailable'); await delay(50); }
  } else if (success) await detail.getByText('技能文件升级完成：1.3.0。', { exact: true }).waitFor();
  else await detail.getByRole('button', { name: '核验并恢复', exact: true }).waitFor();
  if (restart) {
    stage = 'waiting-host-restart';
    await writeFile(`${output}/restart-ready.json`, JSON.stringify({ operationId }));
    const deadline = Date.now() + 120000;
    let resumed;
    while (Date.now() < deadline) {
      try { resumed = JSON.parse(await readFile(`${output}/restart-resume.json`, 'utf8')); break; }
      catch (error) { if (error.code !== 'ENOENT') throw error; }
      await delay(200);
    }
    assert(resumed?.entry, 'Host restart did not complete');
    assert.equal(new URL(resumed.entry).origin, new URL(entry).origin, 'Preserve origin for sessionStorage');
    await unlink(`${output}/restart-resume.json`);
    stage = 'restarted-navigation';
    operationResponses.length = 0;
    await page.goto(resumed.entry, { waitUntil: 'domcontentloaded' });
  } else await page.reload({ waitUntil: 'domcontentloaded' });
  await page.getByText('技能中心', { exact: true }).click();
  await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
  stage = 'restarted-history';
  const history = installed.getByRole('region', { name: '未关闭的升级结果', exact: true });
  if (success) {
    await revealReceipt(history.getByText('技能文件升级完成：1.3.0。', { exact: true }));
    await assertDetails(history, 'upgraded', '1.3.0');
    assert.equal(submissions, 1); assert.equal(recoveries, 0); assert.deepEqual(errors, []);
    await page.screenshot({ path: `${output}/upgrade-success-restarted.png` });
    await writeFile(`${output}/upgrade-result.json`, JSON.stringify({ passed: true, operationId, expectedTargets,
      submissions, recoveries, hostRestartRequested: restart, originalOperationDetailsVerified: true, verifiedDetails, errors }, null, 2));
  } else {
  await history.getByRole('button', { name: '核验并恢复', exact: true }).waitFor();
  await assertDetails(history, 'unknown', null);
  await page.route('**/recoverOperation', async route => {
    const response = await route.fetch();
    const result = (await response.json()).result;
    if (expectBusy && recoveries === 1) {
      assert.equal(result.ok, false, 'Unexpired execution lease must refuse recovery');
      assert.equal(result.error.code, 'skillhub/installation-unavailable');
      assert.equal(result.error.details.retryable, true);
      await route.fulfill({ response });
      return;
    }
    assert.equal(result.ok, true, `Recovery rejected: ${JSON.stringify(result)}`);
    assert.equal(result.value.operationId, operationId);
    assert.equal(result.value.recovery, 'restored');
    await route.abort('failed');
  });
  stage = 'explicit-recovery';
  await history.getByRole('button', { name: '核验并恢复', exact: true }).click();
  if (expectBusy) {
    await history.getByText('恢复结果未确认，请查询原操作并核对完整目标范围。', { exact: true }).waitFor();
    assert.equal(await history.getByText('升级未完成，恢复状态：已恢复旧版本。', { exact: true }).count(), 0);
    await page.screenshot({ path: `${output}/upgrade-recovery-busy.png` });
    // The production lock retains its 10s stale window. Retry is a second user action.
    await delay(11000);
    assert.equal(recoveries, 1, 'Waiting for the lease must not auto-submit recovery');
    stage = 'explicit-recovery';
  await history.getByRole('button', { name: '核验并恢复', exact: true }).click();
  }
  await history.getByText('升级未完成，恢复状态：已恢复旧版本。', { exact: true }).waitFor();
  await installed.getByText('已安装版本 1.2.0', { exact: true }).first().waitFor();
  assert.equal(await installed.getByText('已安装版本 1.2.0', { exact: true }).count(), expectedTargets);
  await history.getByText('恢复结果未确认，请查询原操作并核对完整目标范围。', { exact: true }).waitFor({ state: 'hidden' });
  await assertDetails(history, 'restored', null);
  await page.screenshot({ path: `${output}/upgrade-restored.png` });
  stage = 'close-result';
  await history.getByRole('button', { name: '关闭本次结果', exact: true }).click();
  await history.waitFor({ state: 'hidden' });
  const focusStarted = Date.now();
  const focusDeadline = focusStarted + 3000;
  const scopeFilter = installed.getByRole('combobox', { name: '筛选安装范围' });
  while (!await scopeFilter.evaluate(element => element === document.activeElement) && Date.now() < focusDeadline) await delay(25);
  assert(await scopeFilter.evaluate(element => element === document.activeElement), 'Closing the result must restore focus to the scope filter');
  assert.equal(submissions, 1);
  assert.equal(recoveries, expectBusy ? 2 : 1);
  assert.deepEqual(errors, []);
  const result = { passed: true, operationId, submissions, recoveries, expectedTargets, historyAfterReload: true,
    hostRestartRequested: restart, forcedProcessExit: crash, leaseRefusalAndExplicitRetryVerified: expectBusy,
    originalOperationDetailsVerified: true, verifiedDetails, recoveryResponseLossVerified: true, oldVersionVisible: true, closeFocusVerified: true, focusWaitMs: Date.now() - focusStarted, errors };
  await writeFile(`${output}/upgrade-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
  }
} catch (error) {
  await writeFile(`${output}/upgrade-failure-result.json`, JSON.stringify({ passed: false, stage, operationId, verifiedDetails, errors, activeElement: await page.evaluate(() => ({ tag: document.activeElement?.tagName, label: document.activeElement?.getAttribute('aria-label') })) }, null, 2));
  await page.screenshot({ path: `${output}/upgrade-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally {
  await writeFile(state, 'current');
  await browser.close();
}
