import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_SEARCH_FIXTURE_TITLE: title } = process.env;
if (!entry || !output || !title) throw new Error('Set isolated Host, fresh session and output directory');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = [];
const installs = [];
let removals = 0;
const verifyReadFailures = process.env.DSH_PROBE_FAILURE_RETRY === '1';
let inventoryMode = 'real';
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => {
  const path = new URL(request.url()).pathname;
  if (path.endsWith('/startInstall')) installs.push(request.postDataJSON().payload.args.input);
  if (path.endsWith('/executePlan')) removals++;
});
try {
  if (verifyReadFailures) await page.route('**/listInstalled', async route => {
    if (inventoryMode === 'error') { await route.abort('failed'); return; }
    const response = await route.fetch();
    if (inventoryMode !== 'partial') { await route.fulfill({ response }); return; }
    const body = await response.json();
    assert.equal(body.result.ok, true);
    body.result.value = { items: [], complete: false, issues: ['Fixture: project unavailable'], unavailable: ['project'] };
    await route.fulfill({ response, json: body });
  });
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  await page.getByText(title, { exact: true }).click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  await plaza.getByRole('combobox', { name: '安装范围', exact: true }).selectOption('project');
  await plaza.getByRole('button', { name: '安装到当前项目', exact: true }).click();
  await plaza.getByText('当前范围记录版本：1.2.0。文件存在。', { exact: true }).waitFor();
  assert.equal(await plaza.getByRole('button', { name: '重新安装到当前项目', exact: true }).count(), 0);
  await plaza.getByRole('button', { name: '← 返回技能广场', exact: true }).click();
  await plaza.getByRole('button', { name: '已安装', exact: true }).click();
  await plaza.getByRole('combobox', { name: '筛选安装范围' }).selectOption('project');
  await plaza.getByRole('button', { name: '查看已安装详情', exact: true }).click();
  await plaza.getByRole('button', { name: '卸载此安装位置', exact: true }).click();
  await page.getByRole('dialog', { name: '确认卸载技能', exact: true }).getByRole('button', { name: '确认卸载', exact: true }).click();
  await plaza.getByText('此安装位置已卸载。', { exact: true }).waitFor();
  await plaza.getByRole('button', { name: '关闭本次卸载结果', exact: true }).click();
  await plaza.getByRole('button', { name: '← 返回已安装', exact: true }).click();
  if (verifyReadFailures) inventoryMode = 'partial';
  await plaza.getByRole('button', { name: '浏览技能', exact: true }).click();
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  await plaza.getByRole('combobox', { name: '安装范围', exact: true }).selectOption('project');
  if (verifyReadFailures) {
    const readError = plaza.getByText('当前安装记录无法完整核验，请重试或前往已安装查看。', { exact: true });
    await readError.waitFor();
    for (const mode of ['partial', 'error']) {
      if (mode === 'error') {
        inventoryMode = mode;
        const failed = page.waitForEvent('requestfailed', request => new URL(request.url()).pathname.endsWith('/listInstalled'));
        await plaza.getByRole('button', { name: '核验当前安装状态', exact: true }).click(); await failed;
        await readError.waitFor();
      }
      assert.equal(await plaza.getByRole('button', { name: '重新安装到当前项目', exact: true }).count(), 0);
      assert.equal(await plaza.getByText('当前范围已无此技能的安装记录。', { exact: true }).count(), 0);
      assert.equal(installs.length, 1);
      await page.screenshot({ path: `${output}/reinstall-${mode}-blocked.png` });
    }
    inventoryMode = 'real';
    await plaza.getByRole('button', { name: '核验当前安装状态', exact: true }).click();
  }
  await plaza.getByText('当前范围已无此技能的安装记录。', { exact: true }).waitFor();
  assert.equal(installs.length, 1, 'Returning after removal must not automatically reinstall');
  await page.screenshot({ path: `${output}/reinstall-ready.png` });
  await plaza.getByRole('button', { name: '重新安装到当前项目', exact: true }).click();
  await plaza.getByText('当前范围记录版本：1.2.0。文件存在。', { exact: true }).waitFor();
  assert.equal(installs.length, 2);
  assert.equal(removals, 1);
  assert.equal(installs[0].mode, 'new'); assert.equal(installs[1].mode, 'new');
  assert.notEqual(installs[0].requestKey, installs[1].requestKey);
  await page.reload({ waitUntil: 'domcontentloaded' });
  await page.getByText('SkillHub', { exact: true }).click();
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  await plaza.getByRole('combobox', { name: '安装范围', exact: true }).selectOption('project');
  await plaza.getByText('当前范围记录版本：1.2.0。文件存在。', { exact: true }).waitFor();
  assert.equal(installs.length, 2, 'Reload must query the replacement receipt without submitting');
  const controls = await plaza.getByRole('button', { name: '核验当前安装状态', exact: true }).evaluate(element => {
    const container = element.closest('.skillhub-controls');
    const style = getComputedStyle(element);
    return { height: element.getBoundingClientRect().height, font: style.fontFamily,
      containerFont: container ? getComputedStyle(container).fontFamily : null };
  });
  assert.ok(controls.height >= 40, 'Catalog installation controls must use the shared touch target');
  assert.equal(controls.font, controls.containerFont);
  assert.deepEqual(errors, []);
  await page.screenshot({ path: `${output}/reinstall-completed.png` });
  const result = { passed: true, partialAndFailedReadsBlocked: verifyReadFailures, installSubmissions: installs.length, removals, distinctRequestKeys: true, reloadNoResubmit: true, controls, errors };
  await writeFile(`${output}/reinstall-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/reinstall-failure.png` });
  console.log(JSON.stringify({ error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally { await browser.close(); }
