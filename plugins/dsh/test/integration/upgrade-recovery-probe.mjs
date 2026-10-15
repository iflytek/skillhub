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
const restart = process.env.DSH_PROBE_RESTART === '1';
const crash = process.env.DSH_UPGRADE_CRASH_FIXTURE === '1';
const expectBusy = process.env.DSH_EXPECT_RECOVERY_BUSY === '1';
assert(!crash || restart, 'Crash verification requires Host restart');
assert(!expectBusy || crash, 'Lease contention probe requires forced Host exit');
const errors = [];
let submissions = 0;
let recoveries = 0;
let operationId;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => {
  const path = new URL(request.url()).pathname;
  if (path.endsWith('/executePlan')) submissions++;
  if (path.endsWith('/recoverOperation')) recoveries++;
});
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const welcome = page.getByRole('button', { name: 'Continue', exact: true });
  const configureLater = page.getByRole('button', { name: 'Configure later', exact: true });
  await welcome.or(configureLater).first().waitFor();
  if (await welcome.isVisible()) await welcome.click();
  await configureLater.click();
  if (!await page.getByText(title, { exact: true }).isVisible()) await page.getByText(workspace, { exact: true }).click();
  await page.getByText(title, { exact: true }).click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: '已安装', exact: true }).click();
  const installed = plaza.getByRole('region', { name: '已安装技能', exact: true });
  await installed.getByRole('combobox', { name: '筛选安装范围' }).selectOption('project');
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
  await detail.getByRole('button', { name: '确认升级', exact: true }).click();
  if (crash) {
    const deadline = Date.now() + 15000;
    while (!operationId) { assert(Date.now() < deadline, 'Upgrade acceptance unavailable'); await delay(50); }
  } else await detail.getByRole('button', { name: '核验并恢复', exact: true }).waitFor();
  if (restart) {
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
    await page.goto(resumed.entry, { waitUntil: 'domcontentloaded' });
  } else await page.reload({ waitUntil: 'domcontentloaded' });
  await page.getByText('SkillHub', { exact: true }).click();
  await plaza.getByRole('button', { name: '已安装', exact: true }).click();
  const history = installed.getByRole('region', { name: '未关闭的升级结果', exact: true });
  await history.getByRole('button', { name: '核验并恢复', exact: true }).waitFor();
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
  await history.getByRole('button', { name: '核验并恢复', exact: true }).click();
  if (expectBusy) {
    await history.getByText('恢复结果未确认，请查询原操作并核对完整目标范围。', { exact: true }).waitFor();
    assert.equal(await history.getByText('升级未完成，恢复状态：已恢复旧版本。', { exact: true }).count(), 0);
    await page.screenshot({ path: `${output}/upgrade-recovery-busy.png` });
    // The production lock retains its 10s stale window. Retry is a second user action.
    await delay(11000);
    assert.equal(recoveries, 1, 'Waiting for the lease must not auto-submit recovery');
    await history.getByRole('button', { name: '核验并恢复', exact: true }).click();
  }
  await history.getByText('升级未完成，恢复状态：已恢复旧版本。', { exact: true }).waitFor();
  await installed.getByText('已安装版本 1.2.0', { exact: true }).first().waitFor();
  assert.equal(await installed.getByText('已安装版本 1.2.0', { exact: true }).count(), expectedTargets);
  await history.getByText('恢复结果未确认，请查询原操作并核对完整目标范围。', { exact: true }).waitFor({ state: 'hidden' });
  await page.screenshot({ path: `${output}/upgrade-restored.png` });
  await history.getByRole('button', { name: '关闭本次结果', exact: true }).click();
  await history.waitFor({ state: 'hidden' });
  assert(await installed.getByRole('combobox', { name: '筛选安装范围' }).evaluate(element => element === document.activeElement));
  assert.equal(submissions, 1);
  assert.equal(recoveries, expectBusy ? 2 : 1);
  assert.deepEqual(errors, []);
  const result = { passed: true, operationId, submissions, recoveries, expectedTargets, historyAfterReload: true,
    hostRestartRequested: restart, forcedProcessExit: crash, leaseRefusalAndExplicitRetryVerified: expectBusy,
    recoveryResponseLossVerified: true, oldVersionVisible: true, closeFocusVerified: true, errors };
  await writeFile(`${output}/upgrade-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/upgrade-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally {
  await writeFile(state, 'current');
  await browser.close();
}
