import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const entry = process.env.DSH_PROBE_URL;
const output = process.env.DSH_PROBE_OUTPUT;
const title = process.env.DSH_INSTALL_FIXTURE_TITLE;
if (!entry || !output || !title) throw new Error('Set isolated Host URL, output and install fixture title');
const browser = await chromium.launch({ headless: true,
  ...(process.env.DSH_PROBE_CHROMIUM ? { executablePath: process.env.DSH_PROBE_CHROMIUM } : {}),
});
// Host tool-group selectors below use its English locale; don't inherit the OS locale.
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'en-US' });
const errors = [];
let writes = 0;
let queries = 0;
let updateChecks = 0;
let upgradeSubmissions = 0;
let upgradeVerified = false;
let upgradeResponseLossVerified = false;
const updateScenarios = [];
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => {
  const path = new URL(request.url()).pathname;
  if (path.endsWith('/startInstall')) writes++;
  if (path.endsWith('/getOperation')) queries++;
  if (path.endsWith('/checkUpdate')) updateChecks++;
  if (path.endsWith('/executePlan')) upgradeSubmissions++;
});
const card = page.getByRole('region', { name: 'SkillHub 安装结果' });
async function expand() {
  if (!await card.isVisible()) {
    const process = page.locator('button[data-turn-process="1"]');
    await process.waitFor();
    if (await process.getAttribute('aria-expanded') === 'false') await process.click();
  }
  if (!await card.isVisible()) await page.getByText('Called tools', { exact: true }).click();
  await card.getByText('技能文件安装完成。', { exact: true }).waitFor();
  await card.getByText(/^(上次核验时，DSH 已发现|尚未确认 DSH 发现)此操作对应的技能内容。$/).waitFor();
}
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const welcome = page.getByRole('button', { name: 'Continue', exact: true });
  const configureLater = page.getByRole('button', { name: 'Configure later', exact: true });
  await welcome.or(configureLater).first().waitFor();
  if (await welcome.isVisible()) await welcome.click();
  await configureLater.click();
  if (!await page.getByText(title, { exact: true }).waitFor({ timeout: 3000 }).then(() => true, () => false)) {
    const workspace = process.env.DSH_INSTALL_FIXTURE_WORKSPACE;
    if (!workspace) throw new Error('Set DSH_INSTALL_FIXTURE_WORKSPACE to expand the fixture project');
    await page.getByText(workspace, { exact: true }).click();
  }
  await page.getByText(title, { exact: true }).click();
  await expand();
  assert(queries > 0);
  const cardControl = await card.evaluate(element => {
    const button = element.querySelector('button');
    return { font: getComputedStyle(element).fontFamily, buttonFont: getComputedStyle(button).fontFamily,
      buttonHeight: button.getBoundingClientRect().height };
  });
  assert.equal(cardControl.font, cardControl.buttonFont);
  assert.ok(cardControl.buttonHeight >= 40);

  await card.getByRole('button', { name: '刷新发现状态', exact: true }).click();
  await card.getByText(/^(上次核验时，DSH 已发现|尚未确认 DSH 发现)此操作对应的技能内容。$/).waitFor();
  await card.getByText('最近核验：', { exact: false }).waitFor();
  assert.equal(writes, 0, 'Discovery refresh must not submit installation');

  await card.getByText('操作信息', { exact: true }).click();
  const operationId = await card.locator('code').innerText();
  assert.match(operationId, /^[0-9a-f-]{36}$/);
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
  await card.getByRole('button', { name: '复制操作 ID', exact: true }).click();
  await card.getByText('操作 ID 已复制。', { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => navigator.clipboard.readText()), operationId);
  await page.screenshot({ path: `${output}/install-tool-card.png` });
  const before = queries;
  await page.reload({ waitUntil: 'domcontentloaded' });
  const later = page.getByRole('button', { name: 'Configure later', exact: true });
  if (await later.isVisible()) await later.click();
  await expand();
  assert(queries > before, 'Reload must query the Host again');
  await card.getByText('操作信息', { exact: true }).click();
  assert.equal(await card.locator('code').innerText(), operationId);
  assert.equal(writes, 0, 'History must not submit installation');
  assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/install-tool-card-reloaded.png` });
  let installedListVerified = false;
  if (process.env.DSH_INSTALLED_LIST_PROBE === '1') {
    await card.getByRole('button', { name: '在已安装中查看', exact: true }).click();
    const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
    const installed = plaza.getByRole('region', { name: '已安装技能' });
    await installed.getByRole('combobox', { name: '筛选安装范围' }).selectOption('project');
    await installed.getByText('document-helper', { exact: true }).waitFor();
    await installed.getByText('已安装版本 1.2.0', { exact: true }).waitFor();
    await installed.getByRole('cell', { name: '当前项目', exact: true }).waitFor();
    await installed.getByRole('cell', { name: '文件存在', exact: true }).waitFor();
    await page.screenshot({ path: `${output}/installed-list.png` });
    const detailButton = installed.getByRole('button', { name: '查看已安装详情', exact: true });
    await detailButton.click();
    const installedDetail = plaza.getByRole('region', { name: '已安装技能详情' });
    if (process.env.DSH_INSTALLED_OFFLINE_PROBE === '1') {
      await installedDetail.getByText('远端详情暂时不可用，以上保留本地安装信息。未切换到其他版本。', { exact: true }).waitFor();
      await installedDetail.getByText('@demo/document-helper · 已安装版本 1.2.0', { exact: true }).waitFor();
      await page.screenshot({ path: `${output}/installed-detail-offline.png` });
    } else {
      await installedDetail.getByRole('heading', { name: '文档助手', exact: true }).waitFor();
      assert.equal(updateChecks, 0, 'Opening installed detail must not check updates automatically');
      await installedDetail.getByRole('button', { name: '检查更新', exact: true }).click();
      await installedDetail.getByText('版本及记录指纹与来源当前发布一致。', { exact: true }).waitFor();
      assert.equal(updateChecks, 1, 'One explicit click must submit one update check');
      await page.screenshot({ path: `${output}/installed-detail.png` });
      if (process.env.DSH_FIXTURE_UPDATE_STATE) {
        for (const [scenario, state, reason, message] of [
          ['newer', 'upgradable', 'newer-version', '发现新版本 1.3.0。升级前仍需核验安装目标和本地内容。'],
          ['older', 'blocked', 'remote-older', '远端版本比已安装版本更旧，不执行降级。'],
          ['changed', 'blocked', 'same-version-content-changed', '同一版本的内容发生变化，需要核实，不能直接升级。'],
          ['unknown', 'blocked', 'unknown-order', '无法确定版本先后顺序，请核实发布信息。'],
        ]) {
          await writeFile(process.env.DSH_FIXTURE_UPDATE_STATE, scenario);
          const response = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/checkUpdate'));
          await installedDetail.getByRole('button', { name: '检查更新', exact: true }).click();
          const body = (await (await response).json()).result;
          assert.equal(body.ok, true);
          assert.equal(body.value.state, state);
          assert.equal(body.value.reason, reason);
          await installedDetail.getByText(message, { exact: true }).waitFor();
          await page.screenshot({ path: `${output}/update-${scenario}.png` });
          updateScenarios.push(scenario);
        }
        assert.equal(updateChecks, 5);
        if (process.env.DSH_UPGRADE_PROBE === '1') {
          await writeFile(process.env.DSH_FIXTURE_UPDATE_STATE, 'newer');
          await installedDetail.getByRole('button', { name: '检查更新', exact: true }).click();
          const upgrade = installedDetail.getByRole('region', { name: '升级技能' });
          const prepare = async () => {
            const response = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/prepareUpgrade'));
            await upgrade.getByRole('button', { name: '生成升级计划', exact: true }).click();
            const body = (await (await response).json()).result;
            assert.equal(body.ok, true);
            assert.equal(body.value.status, 'ready', JSON.stringify(body));
            assert.equal(body.value.targets.length, 1);
            assert.equal(body.value.candidateVersion, '1.3.0');
            await upgrade.getByRole('button', { name: '确认升级', exact: true }).waitFor();
          };
          await prepare();
          await page.screenshot({ path: `${output}/upgrade-plan.png` });
          await upgrade.getByRole('button', { name: '取消', exact: true }).click();
          assert.equal(upgradeSubmissions, 0, 'Cancel must not submit an upgrade');
          await prepare();
          let acceptedUpgradeId;
          if (process.env.DSH_UPGRADE_RESPONSE_LOSS === '1') {
            await page.route('**/executePlan', async route => {
              const response = await route.fetch();
              const body = (await response.json()).result;
              assert.equal(body.ok, true);
              if (!acceptedUpgradeId) {
                acceptedUpgradeId = body.value.operationId;
                await route.abort('failed');
              } else {
                assert.equal(body.value.operationId, acceptedUpgradeId, 'Retry must return the original operation');
                await route.fulfill({ response });
              }
            });
          }
          await upgrade.getByRole('button', { name: '确认升级', exact: true }).click();
          if (process.env.DSH_UPGRADE_RESPONSE_LOSS === '1') {
            await upgrade.getByRole('button', { name: '查询原升级请求', exact: true }).waitFor();
            await page.reload({ waitUntil: 'domcontentloaded' });
            const later = page.getByRole('button', { name: 'Configure later', exact: true });
            if (await later.isVisible()) await later.click();
            await page.getByText('SkillHub', { exact: true }).click();
            await plaza.getByRole('button', { name: '已安装', exact: true }).click();
            await installed.getByRole('combobox', { name: '筛选安装范围' }).selectOption('project');
            const deadline = Date.now() + 15_000;
            while (!await installed.getByText('已安装版本 1.3.0', { exact: true }).isVisible() && Date.now() < deadline) {
              await installed.getByRole('button', { name: '刷新已安装', exact: true }).click();
              await page.waitForTimeout(100);
            }
            await installed.getByText('已安装版本 1.3.0', { exact: true }).waitFor();
            await detailButton.click();
            await upgrade.getByRole('button', { name: '查询原升级请求', exact: true }).click();
            upgradeResponseLossVerified = true;
          }
          await upgrade.getByText('技能文件升级完成：1.3.0。', { exact: true }).waitFor({ timeout: 30_000 });
          await installedDetail.getByText(/ · 已安装版本 1\.3\.0$/).waitFor();
          assert.equal(await installedDetail.getByText('安装记录已变化或暂时无法读取，请返回列表刷新。', { exact: true }).count(), 0);
          assert.equal(upgradeSubmissions, upgradeResponseLossVerified ? 2 : 1);
          if (upgradeResponseLossVerified) {
            await upgrade.getByText(`操作 ID：${acceptedUpgradeId}`, { exact: true }).waitFor();
            await upgrade.getByRole('button', { name: '关闭本次结果', exact: true }).click();
            await upgrade.waitFor({ state: 'hidden' });
          }
          await page.screenshot({ path: `${output}/upgrade-completed.png` });
          upgradeVerified = true;
        }
        await writeFile(process.env.DSH_FIXTURE_UPDATE_STATE, 'current');
      }
    }
    await installedDetail.getByRole('button', { name: '← 返回已安装', exact: true }).click();
    assert(await detailButton.evaluate(element => element === document.activeElement), 'Installed detail must restore focus');
    await installed.getByRole('button', { name: '刷新已安装', exact: true }).click();
    await installed.getByText('document-helper', { exact: true }).waitFor();
    if (upgradeVerified) await installed.getByText('已安装版本 1.3.0', { exact: true }).waitFor();
    await plaza.getByRole('button', { name: '返回会话', exact: true }).click();
    installedListVerified = true;
  }
  let staleSelectionFailure = false;
  if (process.env.DSH_INSTALL_FAILURE_FIXTURE_TITLE) {
    await page.getByText(process.env.DSH_INSTALL_FAILURE_FIXTURE_TITLE, { exact: true }).click();
    const failure = page.getByRole('region', { name: 'SkillHub 安装请求未确认' });
    if (!await failure.isVisible()) {
      const processButton = page.locator('button[data-turn-process="1"]');
      await processButton.waitFor();
      if (await processButton.getAttribute('aria-expanded') === 'false') await processButton.click();
    }
    if (!await failure.isVisible()) await page.getByText('Called tools', { exact: true }).click();
    await failure.getByText('原技能选择或会话上下文已失效，请在当前会话重新查看并选择技能。', { exact: true }).waitFor();
    assert.equal(await failure.getByRole('button', { name: '查询原操作', exact: true }).count(), 0);
    assert.equal(await failure.locator('code').count(), 0);
    assert.equal(writes, 0);
    assert.deepEqual(errors, []);
    await page.screenshot({ path: `${output}/install-tool-card-failed.png` });
    staleSelectionFailure = true;
  }
  const result = { passed: true, nativeInstallToolView: true, cardControl, hostOutcomeQueried: true,
    operationIdCopied: true, installedNavigationVerified: installedListVerified,
    persistedReplay: true, stableOperationId: true, staleSelectionFailure, installedListVerified,
    updateCheckVerified: updateChecks === 1 + updateScenarios.length + Number(upgradeVerified), updateScenarios, upgradeVerified, upgradeSubmissions, upgradeResponseLossVerified,
    installedOfflineVerified: installedListVerified && process.env.DSH_INSTALLED_OFFLINE_PROBE === '1', installSubmissions: writes, errors };
  await writeFile(`${output}/install-card-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/install-card-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally {
  if (process.env.DSH_FIXTURE_UPDATE_STATE) await writeFile(process.env.DSH_FIXTURE_UPDATE_STATE, 'current');
  await browser.close();
}
