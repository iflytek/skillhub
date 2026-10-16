import { selectValue, selectedValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { readFile, stat, writeFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { join, resolve, sep } from 'node:path';
import { tmpdir } from 'node:os';
import { chromium } from 'playwright';

const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_SEARCH_FIXTURE_TITLE: title,
  DSH_FIXTURE_UPDATE_STATE: state } = process.env;
if (!entry || !output || !title || !state) throw new Error('Set isolated Host, session, output and fixture update state');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const twoTargets = process.env.DSH_UPGRADE_TWO_TARGETS === '1';
const complete = process.env.DSH_COMPLETE_LIFECYCLE === '1';
const mobile = process.env.DSH_LIFECYCLE_MOBILE === '1';
const mobileStages = [];
async function checkMobile(locator, stage) {
  if (!mobile) return;
  const width = await locator.evaluate(element => ({ client: element.clientWidth, scroll: element.scrollWidth }));
  assert(width.client > 0 && width.scroll <= width.client + 1, `${stage}: ${JSON.stringify(width)}`);
  mobileStages.push({ stage, ...width });
  await page.screenshot({ path: `${output}/mobile-${stage}.png` });
}
const namespace = process.env.DSH_FIXTURE_NAMESPACE ?? 'demo';
const project = process.env.DSH_FIXTURE_PROJECT;
const inventoryPath = process.env.DSH_FIXTURE_INVENTORY;
if (complete) {
  assert(!twoTargets, 'Complete lifecycle fixture requires one isolated project');
  assert(project?.startsWith(resolve(tmpdir()) + sep));
  assert(inventoryPath?.startsWith(resolve(tmpdir()) + sep));
}
const originalInventory = complete ? JSON.parse(await readFile(inventoryPath, 'utf8')) : undefined;
const diskStages = [];
const verifyFiles = async (version, resource) => {
  if (!complete) return;
  const dir = join(project, '.dsh/skills/document-helper');
  const metadata = JSON.parse(await readFile(join(dir, '.skillhub/metadata.json'), 'utf8'));
  assert.equal(metadata.namespace, namespace); assert.equal(metadata.version, version);
  assert.equal(await readFile(join(dir, 'references/note.md'), 'utf8'), resource);
  const aggregate = createHash('sha256');
  for (const name of Object.keys(metadata.files).sort()) {
    const hash = createHash('sha256').update(await readFile(join(dir, name))).digest('hex');
    assert.equal(hash, metadata.files[name]); aggregate.update(`${name}:${hash}\n`);
  }
  assert.equal(metadata.fingerprint, 'sha256:' + aggregate.digest('hex'));
  const inventory = JSON.parse(await readFile(inventoryPath, 'utf8'));
  const own = inventory.items.filter(item => item.namespace === namespace);
  assert.equal(own.length, 1); assert.equal(own[0].version, version);
  assert.equal(own[0].targets.length, 1); assert.equal(own[0].targets[0].installDir, dir);
  diskStages.push({ version, resourceVerified: true, fingerprintVerified: true, oneProjectTarget: true });
};
const errors = [];
let installs = 0;
let upgrades = 0;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => {
  const path = new URL(request.url()).pathname;
  if (path.endsWith('/startInstall')) installs++;
  if (path.endsWith('/executePlan')) upgrades++;
});
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const welcome = page.getByRole('button', { name: /^(Continue|继续)$/ });
  const later = page.getByRole('button', { name: /^(Configure later|稍后配置)$/ });
  await welcome.or(later).first().waitFor();
  if (await welcome.isVisible()) await welcome.click();
  await later.click();
  await page.getByText(title, { exact: true }).click();
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  if (mobile) await page.setViewportSize({ width: 390, height: 844 });
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  assert.equal(await selectedValue(plaza.getByRole('combobox', { name: '安装范围', exact: true })), 'user');
  if (twoTargets) {
    await plaza.getByRole('button', { name: '安装到当前用户', exact: true }).click();
    await page.getByRole('dialog', { name: '确认安装技能？', exact: true }).getByRole('button', { name: '确认安装', exact: true }).click();
    await plaza.getByText('已安装此版本', { exact: true }).waitFor();
  }
  await selectValue(plaza.getByRole('combobox', { name: '安装范围', exact: true }), 'project');
  await plaza.getByRole('button', { name: '安装到当前项目', exact: true }).click();
  await page.getByRole('dialog', { name: '确认安装技能？', exact: true }).getByRole('button', { name: '确认安装', exact: true }).click();
  await plaza.getByText('已安装此版本', { exact: true }).waitFor();
  await verifyFiles('1.2.0', 'Native installation resource');
  await plaza.getByRole('button', { name: '← 返回技能广场', exact: true }).click();
  await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
  await selectValue(plaza.getByRole('combobox', { name: '筛选安装范围' }), 'project');
  await plaza.getByRole('button', { name: '查看已安装详情', exact: true }).waitFor();
  await checkMobile(plaza, 'installed-list');
  await plaza.getByRole('button', { name: '查看已安装详情', exact: true }).click();
  await writeFile(state, 'newer');
  await plaza.getByRole('button', { name: '检查更新', exact: true }).click();
  const response = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/prepareUpgrade'));
  await plaza.getByRole('button', { name: '生成升级计划', exact: true }).click();
  const plan = (await (await response).json()).result;
  assert.equal(plan.ok, true);
  assert.equal(plan.value.status, 'ready');
  assert.equal(plan.value.currentVersion, '1.2.0');
  assert.equal(plan.value.candidateVersion, '1.3.0');
  assert.equal(plan.value.targets.length, twoTargets ? 2 : 1);
  assert.deepEqual(plan.value.targets.map(target => target.scope).sort(), twoTargets ? ['project', 'user'] : ['project']);
  assert.equal(upgrades, 0, 'Planning must not submit an upgrade');
  await page.screenshot({ path: `${output}/upgrade-plan.png` });
  await checkMobile(plaza, 'upgrade-plan');
  await plaza.getByRole('button', { name: '确认升级', exact: true }).click();
  await plaza.getByText('技能文件升级完成：1.3.0。', { exact: true }).waitFor();
  await plaza.getByRole('button', { name: '查看升级后详情', exact: true }).click();
  await plaza.getByText(`@${namespace}/document-helper · 已安装版本 1.3.0`, { exact: true }).waitFor();
  await verifyFiles('1.3.0', 'Native upgraded resource 1.3.0');
  if (mobile) await page.setViewportSize({ width: 1440, height: 1000 });
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.getByText('技能中心', { exact: true }).click();
  if (mobile) await page.setViewportSize({ width: 390, height: 844 });
  await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
  await selectValue(plaza.getByRole('combobox', { name: '筛选安装范围' }), 'project');
  await plaza.getByRole('button', { name: '查看已安装详情', exact: true }).click();
  await plaza.getByText(`@${namespace}/document-helper · 已安装版本 1.3.0`, { exact: true }).waitFor();
  await plaza.getByText('正在核对本地记录并读取固定版本详情…', { exact: true }).waitFor({ state: 'hidden' });
  await plaza.getByRole('tab', { name: '详情', exact: true }).waitFor();
  assert.equal(installs, twoTargets ? 2 : 1);
  assert.equal(upgrades, 1);
  assert.deepEqual(errors, []);
  if (complete && process.env.DSH_DISCOVERY_UI_CHECK === '1') {
    const confirmed = '上次核验时，DSH 已发现此操作对应的技能内容。';
    const unconfirmed = '尚未确认 DSH 发现此操作对应的技能内容。';
    const resource = join(project, '.dsh/skills/document-helper/references/note.md');
    await plaza.getByRole('button', { name: '刷新发现状态', exact: true }).click();
    await plaza.getByText(confirmed, { exact: true }).waitFor();
    const original = await readFile(resource, 'utf8');
    try {
      await writeFile(resource, 'Local resource changed after upgrade');
      await plaza.getByRole('button', { name: '刷新发现状态', exact: true }).click();
      await plaza.getByText(unconfirmed, { exact: true }).waitFor();
    } finally { await writeFile(resource, original); }
    await plaza.getByRole('button', { name: '刷新发现状态', exact: true }).click();
    await plaza.getByText(confirmed, { exact: true }).waitFor();
    assert.equal(installs, 1); assert.equal(upgrades, 1);
  }
  await page.screenshot({ path: `${output}/upgrade-completed.png` });
  await checkMobile(plaza, 'upgrade-completed');
  let uninstalled = false;
  if (complete) {
    await plaza.getByRole('button', { name: '卸载此安装位置', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '确认卸载技能', exact: true });
    await dialog.waitFor();
    await checkMobile(dialog, 'uninstall-confirm');
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    assert.equal(upgrades, 1, 'Cancel must not execute a removal');
    await plaza.getByRole('button', { name: '卸载此安装位置', exact: true }).click();
    await dialog.getByRole('button', { name: '确认卸载', exact: true }).click();
    await plaza.getByText('此安装位置已卸载。', { exact: true }).waitFor();
    assert.equal(upgrades, 2, 'One upgrade and one removal must be submitted');
    await assert.rejects(stat(join(project, '.dsh/skills/document-helper')), { code: 'ENOENT' });
    assert.deepEqual(JSON.parse(await readFile(inventoryPath, 'utf8')), originalInventory);
    await plaza.getByRole('button', { name: '关闭本次卸载结果', exact: true }).click();
    await plaza.getByRole('button', { name: '← 返回已安装', exact: true }).click();
    await plaza.getByText('当前显示 0 项安装记录。', { exact: true }).waitFor();
    await page.screenshot({ path: `${output}/lifecycle-uninstalled.png` });
    await checkMobile(plaza, 'uninstalled');
    assert.deepEqual(errors, []);
    uninstalled = true;
  }
  const result = { passed: true, completeLifecycle: complete, discoveryRefreshVerified: complete && process.env.DSH_DISCOVERY_UI_CHECK === '1', uninstalled, diskStages, inventoryRestored: complete && uninstalled, installs, upgrades, planBeforeExecution: true, projectTargets: 1, userTargets: twoTargets ? 1 : 0,
    currentVersion: '1.2.0', finalVersion: '1.3.0', reloadNoResubmit: true, mobileStages, errors };
  await writeFile(`${output}/upgrade-success-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/upgrade-failure.png` });
  console.log(JSON.stringify({ error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await browser.close(); }
