/** Run against an already-open isolated DSH skill workbench with a modified registry skill.
 * DSH_PROBE_CDP: current browser CDP URL; DSH_PROBE_URL: safe workbench origin.
 * DSH_PROBE_SKILL_SLUG: installed, locally modified skill visible in both lists.
 * DSH_PROBE_OUTPUT: existing private evidence directory. Does not change skill files.
 * Reuses an already-open page with the exact workbench origin; keep one such page open.
 */
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { chromium } from 'playwright';
const { DSH_PROBE_CDP: cdp, DSH_PROBE_URL: entry, DSH_PROBE_SKILL_SLUG: slug, DSH_PROBE_OUTPUT: output } = process.env;
assert(cdp && entry && slug && output, 'Set browser CDP, workbench origin, modified skill slug and output');
const browser = await chromium.connectOverCDP(cdp);
try {
  const page = browser.contexts().flatMap(context => context.pages()).find(page => { try { return new URL(page.url()).origin === new URL(entry).origin; } catch { return false; } });
  assert(page, 'Open the isolated SkillHub workbench first');
  const workbench = page.getByRole('region', { name: 'SkillHub 技能广场' });
  const checks = [];
  const openSkillCard = async () => {
    const card = workbench.locator('.astron-sh-card-open');
    const matches = await card.all();
    const target = await Promise.all(matches.map(async button => ({ button, label: await button.getAttribute('aria-label') })));
    const selected = target.find(({ label }) => label?.includes(`/${slug}`));
    assert(selected, 'Find a card-open control whose accessible label contains the skill slug');
    await selected.button.click();
  };
  const marketDetail = async () => {
    await workbench.getByRole('tab', { name: '发现技能', exact: true }).click();
    await openSkillCard();
    await workbench.getByRole('button', { name: '管理安装', exact: true }).waitFor();
  };
  const openMenuChanges = async () => {
    await workbench.getByRole('button', { name: /^管理(所有项目|仅此项目|当前项目)$/ }).first().click();
    await page.getByRole('menuitem', { name: '查看本地修改', exact: true }).click();
    await workbench.getByRole('region', { name: '本地修改', exact: true }).waitFor();
    assert.equal(await workbench.getByRole('region', { name: '已安装技能详情', exact: true }).count(), 1);
    assert.equal(await workbench.getByRole('tab', { name: '我的技能', exact: true }).getAttribute('data-state'), 'active');
    assert.equal(await page.getByRole('menuitem', { name: '查看本地修改', exact: true }).count(), 0);
  };
  await marketDetail();
  await openMenuChanges(); checks.push('market management menu opens local changes in installed detail');
  await marketDetail();
  await workbench.getByRole('button', { name: /本地修改 ·/ }).first().click();
  await workbench.getByRole('region', { name: '本地修改', exact: true }).waitFor();
  checks.push('market direct modified-files button opens the same preview');
  await marketDetail();
  await workbench.getByRole('tab', { name: '文件', exact: true }).click();
  await workbench.getByRole('button', { name: '管理安装', exact: true }).click();
  await workbench.getByRole('tab', { name: '概览', exact: true }).waitFor();
  await openMenuChanges(); checks.push('files to management to local changes remains reachable');
  await workbench.getByRole('button', { name: '返回已安装', exact: true }).click();
  await openSkillCard();
  await openMenuChanges(); checks.push('installed management menu still opens local changes');
  await writeFile(resolve(output, 'local-changes-entry-result.json'), JSON.stringify({ passed: true, scope: 'isolated real Host/browser; no file writes', checks }, null, 2));
} finally { await browser.close(); }
