import { selectValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_INSTALL_FIXTURE_TITLE: title,
  DSH_INSTALL_FIXTURE_WORKSPACE: workspace } = process.env;
if (!entry || !output || !title || !workspace) throw new Error('Set isolated Host and fixture session');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, colorScheme: 'light' });
const errors = [];
const failureRetry = process.env.DSH_PROBE_FAILURE_RETRY === '1';
let reads = 0;
let checks = 0;
let writes = 0;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => {
  const path = new URL(request.url()).pathname;
  if (/\/(listInstalled|listCatalog)$/.test(path)) reads++;
  if (path.endsWith('/checkUpdate')) checks++;
  if (/\/(startInstall|executePlan|recoverOperation)$/.test(path)) writes++;
});
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  if (!await page.getByText(title, { exact: true }).isVisible()) await page.getByText(workspace, { exact: true }).click();
  await page.getByText(title, { exact: true }).click();
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('tab', { name: '已安装', exact: true }).click();
  const installed = plaza.getByRole('region', { name: '已安装技能', exact: true });
  await selectValue(installed.getByRole('combobox', { name: '筛选安装范围' }), 'project');
  await installed.getByRole('button', { name: '刷新已安装', exact: true }).waitFor();
  await installed.locator('[aria-label="本地安装记录"]').waitFor();
  assert.equal(checks, 0, 'Opening installed records must not check updates');
  const before = reads;
  const filter = installed.getByRole('searchbox', { name: '过滤已安装技能' });
  await filter.fill('does-not-exist');
  await installed.getByText('没有匹配的本地安装记录，请调整关键词或安装范围。', { exact: true }).waitFor();
  await filter.fill('DOCUMENT-HELPER');
  const detailButton = installed.getByRole('button', { name: '查看已安装详情', exact: true });
  await detailButton.waitFor();
  assert.equal(reads, before, 'Local filtering must not request Registry or inventory');
  await detailButton.click();
  await plaza.getByRole('button', { name: '← 返回已安装', exact: true }).click();
  assert.equal(await filter.inputValue(), 'DOCUMENT-HELPER');
  assert(await detailButton.evaluate(element => element === document.activeElement));
  await installed.getByRole('button', { name: '清空过滤', exact: true }).click();
  if (process.env.DSH_FIXTURE_UPDATE_STATE) {
    await writeFile(process.env.DSH_FIXTURE_UPDATE_STATE, 'newer');
    await installed.getByRole('button', { name: '检查更新', exact: true }).click();
    await installed.getByText(/^可升级至 1\.3\.0/).waitFor();
    assert.equal(checks, 1);
    if (failureRetry) {
      await page.route('**/checkUpdate', route => route.abort('failed'), { times: 1 });
      await installed.getByRole('button', { name: '检查更新', exact: true }).click();
      await installed.getByText(/更新检查未完成|Failed to fetch|fetch failed/).waitFor();
      assert.equal(await installed.getByRole('button', { name: '升级', exact: true }).count(), 0,
        'A failed recheck must not leave a cached upgrade action enabled');
      await installed.getByText(/^上次成功检查/).waitFor();
      await page.screenshot({ path: `${output}/installed-recheck-failed.png` });
      assert.equal(checks, 2);
      await installed.getByRole('button', { name: '检查更新', exact: true }).click();
      await installed.getByText(/^可升级至 1\.3\.0/).waitFor();
      assert.equal(checks, 3);
    }
    await installed.getByRole('button', { name: '升级', exact: true }).click();
    const upgradeArea = plaza.getByLabel('升级操作区域', { exact: true });
    assert(await upgradeArea.evaluate(element => element === document.activeElement));
    await upgradeArea.getByRole('button', { name: '生成升级计划', exact: true }).click();
    await upgradeArea.getByRole('button', { name: '确认升级', exact: true }).waitFor();
    await upgradeArea.getByRole('button', { name: '取消', exact: true }).click();
    await plaza.getByRole('button', { name: '← 返回已安装', exact: true }).click();
    assert(await installed.getByRole('button', { name: '升级', exact: true }).evaluate(element => element === document.activeElement));
    await installed.getByRole('button', { name: '卸载', exact: true }).click();
    const removalArea = plaza.getByLabel('卸载操作区域', { exact: true });
    assert(await removalArea.evaluate(element => element === document.activeElement));
    await removalArea.getByRole('button', { name: '卸载此安装位置', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    await plaza.getByRole('button', { name: '← 返回已安装', exact: true }).click();
    assert(await installed.getByRole('button', { name: '卸载', exact: true }).evaluate(element => element === document.activeElement));
    assert.equal(checks, failureRetry ? 3 : 1, 'Navigation must reuse the explicit update check');
  }
  await page.screenshot({ path: `${output}/installed-wide.png` });
  await page.setViewportSize({ width: 680, height: 1000 });
  await page.screenshot({ path: `${output}/installed-narrow.png` });
  const narrow = await installed.evaluate(element => ({ width: element.clientWidth,
    scrollWidth: element.scrollWidth, cardDisplay: getComputedStyle(element.querySelector('.astron-sh-installed-card')).display,
    columns: getComputedStyle(element.querySelector('.astron-sh-card-grid')).gridTemplateColumns.split(' ').length }));
  assert(narrow.width <= 680);
  assert(narrow.scrollWidth <= narrow.width + 1, 'Installed view must not overflow horizontally');
  assert.equal(narrow.cardDisplay, 'flex');
  assert.equal(narrow.columns, 1, 'Narrow installed cards must form a single column');
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: `${output}/installed-mobile.png` });
  const mobile = await installed.evaluate(element => ({ width: element.clientWidth, scrollWidth: element.scrollWidth }));
  assert(mobile.scrollWidth <= mobile.width + 1, '390px installed view must not overflow');
  await detailButton.scrollIntoViewIfNeeded();
  const listScroll = await plaza.evaluate(element => element.scrollTop);
  assert(listScroll > 0, 'Navigation probe requires a genuinely scrolled list');
  await detailButton.click();
  await plaza.locator('button:not(:disabled)').filter({ hasText: '重新读取详情' }).waitFor();
  const tabs = plaza.getByRole('tablist', { name: '技能详情', exact: true });
  await tabs.waitFor();
  assert(await tabs.getByRole('tab').evaluateAll(elements => elements.every(element =>
    element.isConnected && element.ownerDocument.getElementById(element.getAttribute('aria-controls')) !== null)));
  const detailsTab = tabs.getByRole('tab', { name: '详情', exact: true });
  await detailsTab.focus();
  await page.keyboard.press('ArrowRight');
  assert.equal(await tabs.getByRole('tab', { name: '版本', exact: true }).getAttribute('aria-selected'), 'true');
  await page.keyboard.press('End');
  assert.equal(await tabs.getByRole('tab', { name: '评论', exact: true }).getAttribute('aria-selected'), 'true');
  await page.keyboard.press('Home');
  assert.equal(await detailsTab.getAttribute('aria-selected'), 'true');
  await page.keyboard.press('Tab');
  assert(await plaza.getByRole('tabpanel', { name: '详情', exact: true }).evaluate(element => element === document.activeElement));
  await plaza.getByRole('button', { name: '← 返回已安装', exact: true }).click();
  const restoredScroll = await plaza.evaluate(element => element.scrollTop);
  assert(Math.abs(restoredScroll - listScroll) <= 1, 'Returning must restore list scroll position');
  assert(await detailButton.evaluate(element => element === document.activeElement));
  await page.emulateMedia({ colorScheme: 'dark' });
  await page.setViewportSize({ width: 1440, height: 1000 });
  const theme = await installed.evaluate(element => ({ foreground: getComputedStyle(element).color,
    buttonForeground: getComputedStyle(element.querySelector('button')).color,
    bodyBackground: getComputedStyle(document.body).backgroundColor,
    rootClass: document.documentElement.className }));
  assert.equal(theme.foreground, theme.buttonForeground);
  await page.screenshot({ path: `${output}/installed-dark-preference.png` });
  let darkDialog;
  if (process.env.DSH_FIXTURE_UPDATE_STATE) {
    await page.setViewportSize({ width: 390, height: 844 });
    await installed.getByRole('button', { name: '卸载', exact: true }).click();
    const removalArea = plaza.getByLabel('卸载操作区域', { exact: true });
    await removalArea.getByRole('button', { name: '卸载此安装位置', exact: true }).click();
    const dialog = page.getByRole('dialog');
    await dialog.waitFor();
    darkDialog = await dialog.evaluate(element => ({ width: element.getBoundingClientRect().width,
      scrollWidth: element.scrollWidth, clientWidth: element.clientWidth, foreground: getComputedStyle(element).color,
      background: getComputedStyle(element).backgroundColor,
      dangerColor: getComputedStyle(element.querySelector('.skillhub-danger')).color,
      cancelFocused: element.querySelector('button') === document.activeElement }));
    assert(darkDialog.width <= 390);
    assert(darkDialog.scrollWidth <= darkDialog.clientWidth + 1);
    assert(darkDialog.cancelFocused);
    const luminance = color => {
      const rgb = color.match(/[\d.]+/g).slice(0, 3).map(Number).map(value => {
        const s = value / 255;
        return s <= .04045 ? s / 12.92 : ((s + .055) / 1.055) ** 2.4;
      });
      return rgb[0] * .2126 + rgb[1] * .7152 + rgb[2] * .0722;
    };
    const textLight = luminance(darkDialog.dangerColor);
    const backgroundLight = luminance(darkDialog.background);
    darkDialog.dangerContrast = (Math.max(textLight, backgroundLight) + .05) / (Math.min(textLight, backgroundLight) + .05);
    assert(darkDialog.dangerContrast >= 4.5, 'Danger label requires readable dark dialog contrast');
    await page.screenshot({ path: `${output}/installed-dark-dialog-mobile.png` });
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
  }
  assert.equal(writes, 0);
  assert.deepEqual(errors, []);
  const result = { passed: true, localFiltering: true, filterPreserved: true, returnFocus: true,
    narrow, mobile, darkDialog, listScroll, restoredScroll, keyboardTabsVerified: true, failureRetry,
    darkPreference: theme, rowActionsVerified: !!process.env.DSH_FIXTURE_UPDATE_STATE, checks, writes, errors };
  await writeFile(`${output}/installed-layout-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/installed-layout-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally {
  if (process.env.DSH_FIXTURE_UPDATE_STATE) await writeFile(process.env.DSH_FIXTURE_UPDATE_STATE, 'current');
  await browser.close();
}
