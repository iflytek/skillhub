import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { writeFile } from 'node:fs/promises';
import { build as buildFixture, serve } from 'bun';

// Run with Bun. This probe owns its fixture server and tests actual account components.
const build = await buildFixture({ entrypoints: [new URL('./account-ui-fixture.tsx', import.meta.url).pathname], target: 'browser', define: { 'process.env.NODE_ENV': '"development"' } });
assert(build.success, 'Fixture must build');
const script = await build.outputs[0].text();
const persistenceBuild = await buildFixture({ entrypoints: [new URL('./account-persistence-ui-fixture.tsx', import.meta.url).pathname], target: 'browser', define: { 'process.env.NODE_ENV': '"development"' } });
assert(persistenceBuild.success, 'Persistence fixture must build');
const persistenceScript = await persistenceBuild.outputs[0].text();
const server = serve({ hostname: '127.0.0.1', port: 0, fetch: request => {
  const path = new URL(request.url).pathname;
  if (path === '/app.js' || path === '/persistence.js') return new Response(path === '/app.js' ? script : persistenceScript, { headers: { 'Content-Type': 'text/javascript' } });
  return new Response(`<!doctype html><html style="color-scheme:light dark"><meta name="viewport" content="width=device-width"><div id="root"></div><script type="module" src="${path === '/persistence' ? '/persistence.js' : '/app.js'}"></script></html>`, { headers: { 'Content-Type': 'text/html' } });
} });
const browser = await chromium.launch({ headless: true, ...(process.env.DSH_PROBE_CHROMIUM ? { executablePath: process.env.DSH_PROBE_CHROMIUM } : {}) });
const errors = [];
let checks = 0;
let namespaceChecks = 0;
let persistenceChecks = 0;
async function fileTransfer(page, files) {
  return page.evaluateHandle(files => {
    const transfer = new DataTransfer();
    for (const file of files) transfer.items.add(new File([file.size === undefined ? file.body ?? 'fixture-package' : new Uint8Array(file.size)], file.name, { type: file.type ?? 'application/zip' }));
    return transfer;
  }, files);
}
async function dropFiles(page, target, files) {
  const transfer = await fileTransfer(page, files);
  try {
    await target.dispatchEvent('dragover', { dataTransfer: transfer });
    await target.dispatchEvent('drop', { dataTransfer: transfer });
  } finally { await transfer.dispose(); }
}
function contrast(foreground, background) {
  const luminance = color => color.match(/[\d.]+/g).slice(0, 3).map(Number).map(v => v / 255).map(v => v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4).reduce((sum, v, i) => sum + v * [0.2126, 0.7152, 0.0722][i], 0);
  const a = luminance(foreground), b = luminance(background);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}
try {
  for (const width of [1280, 563, 375]) for (const theme of ['light', 'dark']) {
    const page = await browser.newPage({ viewport: { width, height: 820 }, colorScheme: theme });
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(server.url.href);
    const dialog = page.getByRole('dialog');
    const otherTheme = theme === 'dark' ? 'light' : 'dark';
    await page.evaluate(mode => { document.documentElement.style.colorScheme = mode; }, otherTheme);
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await page.waitForFunction(mode => getComputedStyle(document.querySelector('[role="dialog"]')).colorScheme === mode, otherTheme);
    await page.evaluate(mode => { document.documentElement.style.colorScheme = mode; }, theme);
    await page.waitForFunction(mode => getComputedStyle(document.querySelector('[role="dialog"]')).colorScheme === mode, theme);
    await dialog.getByRole('button', { name: '更改来源' }).click();
    const config = page.getByRole('form', { name: 'SkillHub 配置' });
    await config.waitFor();
    await page.waitForFunction(() => document.activeElement?.textContent === 'SkillHub 设置');
    assert.equal(await config.evaluate(el => getComputedStyle(el).backgroundColor), theme === 'dark' ? 'rgb(16, 16, 16)' : 'rgb(255, 255, 255)', 'Source editor follows the theme');
    await config.getByRole('radio', { name: '选择来源 2：另一来源' }).check();
    await config.getByRole('button', { name: '保存配置', exact: true }).click();
    await config.getByText('配置已保存。', { exact: true }).waitFor();
    await page.getByRole('button', { name: '返回技能中心' }).click();
    const toggleLabel = mode => mode === 'dark' ? '切换到浅色模式' : '切换到深色模式';
    await page.getByRole('button', { name: toggleLabel(theme), exact: true }).click();
    await page.waitForFunction(mode => getComputedStyle(document.querySelector('.astron-sh-catalog')).colorScheme === mode, otherTheme);
    assert.equal(await page.locator('.astron-sh-catalog').evaluate(el => getComputedStyle(el).backgroundColor), otherTheme === 'dark' ? 'rgb(16, 16, 16)' : 'rgb(255, 255, 255)', 'Workbench background is opaque');
    assert.equal(await page.getByLabel('主题测试通知').evaluate(el => getComputedStyle(el).colorScheme), otherTheme, 'Body portal follows the theme');
    const embedded = await page.getByLabel('宿主会话').evaluate(el => ({ color: getComputedStyle(el.querySelector('p')).color, background: getComputedStyle(el).backgroundColor, scheme: getComputedStyle(el.querySelector('.skillhub-controls')).colorScheme }));
    assert.equal(embedded.scheme, theme, 'Embedded conversation keeps the Host theme');
    assert(contrast(embedded.color, embedded.background) >= 4.5, 'Embedded conversation text retains contrast');
    assert.equal(await page.evaluate(() => document.documentElement.style.colorScheme), theme, 'Plugin toggle does not change DSH theme');
    await page.getByRole('button', { name: toggleLabel(otherTheme), exact: true }).click();
    const layout = async name => {
      await dialog.waitFor();
      const box = await dialog.boundingBox();
      assert(box.x >= 0 && box.x + box.width <= width + 1, `${name}: fits viewport`);
      assert(await dialog.evaluate(el => el.scrollWidth <= el.clientWidth + 1), `${name}: no horizontal overflow`);
      const footer = dialog.locator('footer');
      const buttons = footer.getByRole('button');
      if (await buttons.count() > 1) {
        const [a, b] = await buttons.evaluateAll(items => items.map(el => el.getBoundingClientRect().toJSON()));
        assert(b.x >= a.x + a.width + 7, `${name}: actions separated`);
        assert(Math.abs(a.y - b.y) < 2, `${name}: actions aligned`);
      }
      checks++;
      if (process.env.DSH_PROBE_OUTPUT) await page.screenshot({ path: `${process.env.DSH_PROBE_OUTPUT}/${name}-${width}-${theme}.png` });
    };
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await layout('login');
    assert.equal(await dialog.locator('dd').innerText(), 'https://other.example.test', 'Login uses the saved source');
    const sourceColors = await dialog.locator('dl').evaluate(el => ({ foreground: getComputedStyle(el.querySelector('dt')).color, background: getComputedStyle(el).backgroundColor }));
    assert(contrast(sourceColors.foreground, sourceColors.background) >= 4.5, 'Muted source label retains contrast');
    assert.equal(await dialog.evaluate(el => getComputedStyle(el).backgroundColor), theme === 'dark' ? 'rgb(24, 24, 24)' : 'rgb(255, 255, 255)', 'Dialog inherits Host theme');
    const token = dialog.getByLabel('个人 API Token', { exact: true });
    assert(await token.evaluate(el => document.activeElement === el), 'Token receives initial focus');
    await token.fill('invalid-fixture');
    await token.press('Enter');
    assert(await dialog.getByRole('button', { name: '取消', exact: true }).isDisabled(), 'Cannot close while connecting');
    await dialog.getByRole('alert').waitFor();
    assert.equal(await token.inputValue(), '', 'Token cleared after submission');
    await token.fill('fixture-token');
    await token.press('Enter');
    await dialog.waitFor({ state: 'hidden' });
    await page.getByRole('button', { name: '账户菜单' }).click();
    await page.getByRole('menuitem', { name: '个人设置' }).click();
    await layout('settings');
    assert(await dialog.evaluate(el => el.contains(document.activeElement)), 'Settings traps focus');
    await dialog.getByRole('button', { name: toggleLabel(theme), exact: true }).click();
    await page.waitForFunction(mode => getComputedStyle(document.querySelector('[role="dialog"]')).colorScheme === mode, otherTheme);
    await dialog.getByRole('button', { name: '管理来源' }).click();
    await config.waitFor();
    assert.equal(await config.evaluate(el => getComputedStyle(el).colorScheme), otherTheme, 'Settings source editor keeps the preference');
    await page.getByRole('button', { name: '返回技能中心' }).click();
    await page.getByRole('button', { name: toggleLabel(otherTheme), exact: true }).click();
    await page.getByRole('button', { name: '账户菜单' }).click();
    await page.getByRole('menuitem', { name: '个人设置' }).click();
    await dialog.getByRole('button', { name: '更换个人 Token' }).click();
    assert(await dialog.getByLabel('个人 API Token', { exact: true }).evaluate(el => document.activeElement === el), 'Changing token focuses the input');
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: '账户菜单' }).click();
    await page.getByRole('menuitem', { name: '个人设置' }).click();
    await dialog.getByRole('button', { name: '完成' }).click();
    await page.waitForFunction(() => document.activeElement?.getAttribute('aria-label') === '账户菜单', { timeout: 3000 });
    assert(await page.getByRole('button', { name: '账户菜单' }).evaluate(el => document.activeElement === el), 'Menu trigger regains focus');
    await page.getByRole('button', { name: '上传技能', exact: true }).click();
    await dialog.getByRole('combobox', { name: '目标命名空间' }).waitFor();
    await layout('upload');
    assert.equal(await dialog.getByRole('combobox').count(), 2, 'Namespace and visibility are dropdowns');
    await dialog.getByRole('combobox', { name: '目标命名空间' }).selectOption('team-12');
    await dialog.getByRole('combobox', { name: '目标命名空间' }).selectOption('design');
    await dialog.getByRole('combobox', { name: '目标命名空间' }).focus();
    await page.keyboard.press('Space');
    await page.keyboard.press('Escape');
    assert(await dialog.isVisible(), 'First Escape only dismisses the native dropdown');
    await page.keyboard.press('Escape');
    await dialog.waitFor({ state: 'hidden' });
    await page.getByRole('button', { name: '上传技能', exact: true }).click();
    await dialog.getByRole('combobox', { name: '目标命名空间' }).waitFor();
    const picker = dialog.getByRole('button', { name: '选择技能包文件', exact: true });
    assert(await dialog.getByRole('combobox', { name: '目标命名空间' }).evaluate(select => document.activeElement === select), 'Upload focuses the visible namespace field instead of the hidden file input');
    const transfer = await fileTransfer(page, [{ name: 'fixture.zip' }]);
    await picker.dispatchEvent('dragenter', { dataTransfer: transfer });
    await picker.locator('svg').dispatchEvent('dragenter', { dataTransfer: transfer });
    await picker.locator('svg').dispatchEvent('dragleave', { dataTransfer: transfer });
    assert.equal(await picker.getAttribute('data-dragging'), 'true', 'Moving across dropzone children preserves drag highlight');
    await layout('upload-drag');
    await picker.dispatchEvent('dragleave', { dataTransfer: transfer });
    assert.equal(await picker.getAttribute('data-dragging'), 'false', 'Leaving the dropzone clears drag highlight');
    await transfer.dispose();
    const originalUrl = page.url();
    await dropFiles(page, picker, [{ name: 'dragged.zip' }]);
    const selectedFile = dialog.locator('.astron-sh-upload-file');
    await selectedFile.getByText('dragged.zip', { exact: true }).waitFor();
    await dialog.getByRole('button', { name: '核对上传信息' }).click();
    await dialog.getByRole('region', { name: '确认上传目标' }).waitFor();
    for (const [files, message] of [
      [[{ name: 'wrong.txt', type: 'text/plain' }], '请选择 ZIP 格式的技能包。'],
      [[{ name: 'first.zip' }, { name: 'second.zip' }], '一次只能选择一个 ZIP 技能包。'],
      [[{ name: 'empty.zip', size: 0 }], '请选择非空且不超过 8 MiB 的 ZIP 文件。'],
      [[{ name: 'oversized.zip', size: 8 * 1024 * 1024 + 1 }], '请选择非空且不超过 8 MiB 的 ZIP 文件。'],
    ]) {
      await dropFiles(page, dialog.locator('.astron-sh-upload-dropzone'), files);
      await dialog.getByRole('alert').filter({ hasText: message }).waitFor();
      assert.equal(await selectedFile.count(), 0, 'Rejected selection clears the previous package');
      assert.equal(await dialog.getByRole('region', { name: '确认上传目标' }).count(), 0, 'Rejected selection invalidates the previous confirmation');
      assert(await dialog.getByRole('button', { name: '核对上传信息' }).isDisabled(), 'Invalid files cannot be uploaded');
    }
    await layout('upload-file-error');
    await dropFiles(page, picker, [{ name: 'removable.zip' }]);
    await selectedFile.getByText('removable.zip', { exact: true }).waitFor();
    await selectedFile.getByRole('button', { name: '移除文件', exact: true }).click();
    assert(await picker.evaluate(button => document.activeElement === button), 'Removing the file returns focus to the picker');
    assert.equal(await selectedFile.count(), 0);
    assert(await dialog.getByRole('button', { name: '核对上传信息' }).isDisabled(), 'Removing a file clears its upload target');
    const chooserPromise = page.waitForEvent('filechooser');
    await picker.press('Enter');
    const chooser = await chooserPromise;
    assert.equal(chooser.isMultiple(), false, 'Picker accepts one file');
    const longName = '文件名较长的技能包-'.repeat(12) + '.ZIP';
    await chooser.setFiles({ name: longName, mimeType: 'application/octet-stream', buffer: Buffer.from('fixture-package') });
    await selectedFile.getByText(longName, { exact: true }).waitFor();
    assert.equal(await dialog.getByRole('alert').count(), 0, 'A valid picker selection clears file errors');
    await layout('upload-selected');
    await dialog.getByRole('button', { name: '核对上传信息' }).click();
    await dialog.getByRole('region', { name: '确认上传目标' }).waitFor();
    await dropFiles(page, dialog.locator('.astron-sh-upload-dropzone'), [{ name: 'replacement.zip' }]);
    await selectedFile.getByText('replacement.zip', { exact: true }).waitFor();
    assert.equal(await dialog.getByRole('region', { name: '确认上传目标' }).count(), 0, 'Replacing a valid file also clears confirmation');
    const outsideTransfer = await fileTransfer(page, [{ name: 'outside.zip' }]);
    const prevented = await dialog.evaluate((element, transfer) => {
      const event = new DragEvent('drop', { dataTransfer: transfer, bubbles: true, cancelable: true });
      element.dispatchEvent(event);
      return event.defaultPrevented;
    }, outsideTransfer);
    await outsideTransfer.dispose();
    assert(prevented && page.url() === originalUrl, 'Dropping outside the zone cannot navigate the dialog');
    assert(await selectedFile.getByText('replacement.zip', { exact: true }).isVisible(), 'Outside drops do not replace a selected file');
    await page.evaluate(() => window.pauseNextAccountInspection());
    await dialog.getByRole('button', { name: '核对上传信息' }).click();
    await page.waitForFunction(() => window.isAccountInspectionPaused());
    await dialog.getByRole('combobox', { name: '可见性' }).selectOption('PUBLIC');
    await page.evaluate(() => window.finishAccountInspection());
    await dialog.getByText('正在核对目标技能…', { exact: true }).waitFor({ state: 'hidden' });
    assert.equal(await dialog.getByRole('region', { name: '确认上传目标' }).count(), 0, 'An older verification cannot restore confirmation after visibility changes');
    assert.equal(await dialog.getByRole('combobox', { name: '可见性' }).inputValue(), 'PUBLIC');
    await dialog.getByRole('button', { name: '核对上传信息' }).click();
    await dialog.getByRole('region', { name: '确认上传目标' }).waitFor();
    await layout('upload-confirm');
    await dialog.getByRole('button', { name: '确认上传' }).dblclick();
    await page.waitForFunction(() => document.querySelector('[role="dialog"]')?.getAttribute('aria-busy') === 'true');
    assert(await dialog.getByRole('button', { name: '更换技能包文件', exact: true }).isDisabled(), 'Picker is locked during upload');
    assert(await selectedFile.getByRole('button', { name: '移除文件', exact: true }).isDisabled(), 'Removal is locked during upload');
    await dropFiles(page, dialog.locator('.astron-sh-upload-dropzone'), [{ name: 'busy-replacement.zip' }]);
    assert(await selectedFile.getByText('replacement.zip', { exact: true }).isVisible(), 'Drops during upload cannot replace the submitted file');
    assert.equal((await page.evaluate(() => window.accountWriteCounts())).upload, 1, 'Double click submits one upload');
    await layout('upload-busy');
    await page.evaluate(() => window.finishAccountUpload());
    await dialog.getByRole('heading', { name: '已上传' }).waitFor();
    await layout('upload-receipt');
    await dialog.getByRole('button', { name: '关闭', exact: true }).click();
    await page.waitForFunction(() => document.body.style.pointerEvents !== 'none');
    await page.getByRole('button', { name: '账户菜单' }).click();
    await page.getByRole('menuitem', { name: '我的命名空间' }).click();
    const pageLayout = async name => {
      const root = page.locator('.astron-sh-account-page');
      assert.equal(await root.getByRole('heading', { level: 1 }).count(), 1, 'Page has one primary heading');
      assert(await root.evaluate(el => el.scrollWidth <= el.clientWidth + 1), `${name}: no horizontal overflow`);
      assert(!(await root.innerText()).includes('ARCHIVED'), 'Raw namespace status is not displayed');
      namespaceChecks++;
      if (process.env.DSH_PROBE_OUTPUT) await root.screenshot({ path: `${process.env.DSH_PROBE_OUTPUT}/${name}-${width}-${theme}.png`, style: '[aria-label="主题测试通知"],[aria-label="宿主会话"]{visibility:hidden!important}' });
    };
    await page.getByRole('list', { name: '所属命名空间' }).waitFor();
    await pageLayout('namespace-list');
    await page.getByRole('button', { name: '进入空间' }).first().click();
    await page.getByRole('list', { name: '可安装技能', exact: true }).waitFor();
    await pageLayout('namespace-skills');
    if (width === 1280 && theme === 'light') {
      const teamList = page.getByRole('list', { name: '可安装技能', exact: true });
      const openUpload = async () => {
        await page.locator('.astron-sh-namespace-heading').getByRole('button', { name: '上传技能' }).click();
        await dialog.locator('input[type=file]').setInputFiles({ name: 'example.zip', mimeType: 'application/zip', buffer: Buffer.from('fixture-package') });
        await dialog.getByRole('button', { name: '核对上传信息' }).click();
        await dialog.getByRole('button', { name: '确认上传' }).waitFor();
      };
      for (const [publishImmediately, version] of [[true, '1.0.2'], [false, '1.0.3']]) {
        await openUpload();
        await dialog.getByRole('button', { name: '确认上传' }).click();
        await page.waitForFunction(() => document.querySelector('[role="dialog"]')?.getAttribute('aria-busy') === 'true');
        await page.evaluate(immediate => window.finishAccountUpload(immediate), publishImmediately);
        if (!publishImmediately) {
          await dialog.getByText('UPLOADED', { exact: true }).waitFor();
          assert(!(await page.locator('.astron-sh-namespace-list').innerText()).includes(version), 'Receipt alone does not mark a pending version installable');
          await page.evaluate(() => window.publishAccountVersion());
          await dialog.getByRole('button', { name: '刷新版本状态' }).click();
        }
        await dialog.getByText('PUBLISHED', { exact: true }).waitFor();
        await dialog.getByRole('button', { name: '关闭', exact: true }).click();
        await teamList.getByText(`@design · v${version}`, { exact: true }).waitFor();
      }
      await openUpload();
      const statusReads = await page.evaluate(() => window.accountUploadStatusReads());
      await dialog.getByRole('button', { name: '确认上传' }).click();
      await page.waitForFunction(() => document.querySelector('[role="dialog"]')?.getAttribute('aria-busy') === 'true');
      await page.evaluate(() => window.finishAccountUpload(false, true));
      await dialog.getByText('来源未返回版本 ID。请到来源网页确认审核或发布状态。', { exact: true }).waitFor();
      assert.equal(await dialog.getByRole('button', { name: '刷新版本状态' }).count(), 0);
      assert.equal(await page.evaluate(() => window.accountUploadStatusReads()), statusReads);
      await dialog.getByRole('button', { name: '关闭', exact: true }).click();
      await openUpload();
      const previous = (await page.evaluate(() => window.accountWriteCounts())).upload;
      await page.context().setOffline(true);
      await dialog.getByRole('button', { name: '确认上传' }).click();
      await dialog.getByRole('alert').filter({ hasText: '无法连接' }).waitFor();
      assert.equal((await page.evaluate(() => window.accountWriteCounts())).upload, previous + 1);
      await dialog.getByRole('button', { name: '取消' }).click();
      await page.context().setOffline(false);
      await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
      assert.equal((await page.evaluate(() => window.accountWriteCounts())).upload, previous + 1, 'Reconnection does not replay a failed upload');
    }

    await page.getByRole('button', { name: '我的上传', exact: true }).click();
    await page.getByRole('list', { name: '我的上传', exact: true }).waitFor();
    await pageLayout('namespace-uploads');
    await page.getByRole('button', { name: '上传新版本', exact: true }).click();
    await dialog.waitFor();
    assert(await dialog.getByRole('combobox', { name: '目标命名空间' }).isDisabled(), 'Existing skill keeps its namespace');
    assert(await dialog.getByRole('button', { name: '选择技能包文件', exact: true }).evaluate(button => document.activeElement === button), 'Update upload focuses the picker when its namespace is locked');
    await layout('upload-update');
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    await dialog.waitFor({ state: 'hidden' });
    await page.getByRole('button', { name: '删除', exact: true }).click();
    await layout('delete');
    const testOfflineDelete = width === 1280 && theme === 'light';
    if (testOfflineDelete) {
      await page.context().setOffline(true);
      await dialog.getByRole('button', { name: '删除技能', exact: true }).click();
      await dialog.getByRole('alert').filter({ hasText: '无法连接' }).waitFor();
      await dialog.getByRole('button', { name: '取消' }).click();
      await page.context().setOffline(false);
      await page.getByRole('button', { name: '删除', exact: true }).click();
      assert.equal((await page.evaluate(() => window.accountWriteCounts())).delete, 1, 'Failed delete is not replayed on reconnect');
    }
    await dialog.getByRole('button', { name: '删除技能', exact: true }).dblclick();
    await dialog.waitFor({ state: 'hidden' });
    assert.equal((await page.evaluate(() => window.accountWriteCounts())).delete, testOfflineDelete ? 2 : 1, 'Double click deletes a skill once');
    await page.getByRole('button', { name: '成员管理', exact: true }).focus();
    await page.getByRole('button', { name: '成员管理', exact: true }).press('Enter');
    assert.equal(await page.getByRole('tab', { name: '成员', exact: true }).getAttribute('aria-selected'), 'true', 'Space heading opens member management');
    assert(await page.getByRole('tab', { name: '成员', exact: true }).evaluate(tab => document.activeElement === tab), 'Member management entry moves keyboard focus into the selected section');
    await page.getByText('3 位成员 · 按用户 ID 识别', { exact: true }).waitFor();
    await pageLayout('namespace-members-admin');
    if (width === 1280) {
      await page.locator('.astron-sh-catalog').evaluate(el => { el.style.width = '375px'; });
      const rows = await page.locator('.astron-sh-namespace-member-row').evaluateAll(items => items.map(row => {
        const identity = row.querySelector('.astron-sh-namespace-identity').getBoundingClientRect();
        const bounds = row.getBoundingClientRect();
        return { identityWidth: identity.width, actionsVisible: [...row.querySelectorAll('button')].every(button => { const r = button.getBoundingClientRect(); return r.x >= bounds.x && r.right <= bounds.right; }) };
      }));
      assert(rows.every(row => row.identityWidth > 100 && row.actionsVisible), 'Narrow DSH panel retains member identity and all actions');
      await pageLayout('namespace-members-narrow-panel');
      await page.locator('.astron-sh-catalog').evaluate(el => { el.style.width = ''; });
    }
    assert(await page.getByText('所有者受保护', { exact: true }).isVisible(), 'Owner protection is visible');
    await page.getByRole('tab', { name: '成员', exact: true }).press('ArrowLeft');
    await page.getByRole('tab', { name: '技能', exact: true, selected: true }).waitFor();
    assert.equal(await page.getByRole('tab', { name: '技能', exact: true }).getAttribute('aria-selected'), 'true', 'Tabs support keyboard navigation');
    assert(await page.getByRole('heading', { name: '我的上传', exact: true }).isVisible(), 'Skill scope survives switching sections');
    await page.getByRole('tab', { name: '技能', exact: true }).press('ArrowRight');
    await page.getByRole('button', { name: '添加成员', exact: true }).click();
    const search = dialog.getByLabel('昵称、邮箱或用户 ID');
    await search.fill('候选');
    await search.press('Enter');
    await dialog.getByRole('button', { name: /候选用户/ }).click();
    assert(await dialog.getByRole('button', { name: /候选用户/ }).evaluate(el => {
      const meta = el.querySelector('span').getBoundingClientRect(), box = el.getBoundingClientRect();
      return meta.bottom <= box.bottom && meta.top >= box.top;
    }), 'Candidate email remains inside the button');
    await layout('add-member');
    assert(await dialog.getByRole('button', { name: '确认添加' }).isEnabled(), 'Selected candidate can be added');
    await dialog.getByRole('button', { name: '确认添加' }).dblclick();
    await page.waitForFunction(() => document.querySelector('[role="dialog"]')?.getAttribute('aria-busy') === 'true');
    assert(await search.isDisabled(), 'Search locked during member write');
    assert(await dialog.getByRole('combobox').isDisabled(), 'Role locked during member write');
    assert(await dialog.getByRole('button', { name: /候选用户/ }).isDisabled(), 'Candidate locked during member write');
    await dialog.waitFor({ state: 'hidden' });
    await page.getByText('4 位成员 · 按用户 ID 识别', { exact: true }).waitFor();
    assert.equal((await page.evaluate(() => window.accountWriteCounts())).add, 1, 'Double click adds a member once');
    const candidateRow = page.locator('.astron-sh-namespace-member-row').filter({ hasText: '候选用户' });
    assert(await candidateRow.getByText('成员', { exact: true }).isVisible(), 'Added user appears with the requested member role');
    await page.getByRole('tab', { name: '技能', exact: true }).click();
    await page.getByText('你还没有在此空间上传技能。', { exact: true }).waitFor();
    assert.equal(await page.getByText('已添加成员 candidate', { exact: true }).count(), 0, 'Member feedback does not mask skill empty state');
    await pageLayout('namespace-uploads-empty');
    await page.getByRole('tab', { name: '成员', exact: true }).click();
    await candidateRow.getByRole('button', { name: '修改角色', exact: true }).click();
    await layout('member-role');
    await dialog.getByRole('combobox', { name: '成员角色' }).selectOption('ADMIN');
    await dialog.getByRole('button', { name: '保存角色', exact: true }).dblclick();
    await dialog.waitFor({ state: 'hidden' });
    await candidateRow.getByText('管理员', { exact: true }).waitFor();
    assert.equal((await page.evaluate(() => window.accountWriteCounts())).role, 1, 'Double click changes a role once');
    await candidateRow.getByRole('button', { name: '移除', exact: true }).click();
    await layout('remove-member');
    await dialog.getByRole('button', { name: '取消', exact: true }).click();
    await dialog.waitFor({ state: 'hidden' });
    await page.waitForFunction(button => document.activeElement === button, await candidateRow.getByRole('button', { name: '移除', exact: true }).elementHandle());
    assert(await candidateRow.getByRole('button', { name: '移除', exact: true }).evaluate(button => document.activeElement === button), 'Cancel returns focus to the member action');
    await candidateRow.getByRole('button', { name: '移除', exact: true }).click();
    await dialog.getByRole('button', { name: '移除成员', exact: true }).dblclick();
    await dialog.waitFor({ state: 'hidden' });
    await page.getByText('3 位成员 · 按用户 ID 识别', { exact: true }).waitFor();
    assert.equal((await page.evaluate(() => window.accountWriteCounts())).remove, 1, 'Double click removes a member once');
    assert.equal(await candidateRow.count(), 0, 'Removed member disappears after refetch');
    await page.waitForFunction(() => document.activeElement?.getAttribute('role') === 'tab' && document.activeElement.textContent === '成员');
    assert(await page.getByRole('tab', { name: '成员', exact: true }).evaluate(tab => document.activeElement === tab), 'Successful removal restores focus to the member section instead of the removed row');
    for (const [name, slug, section, notice] of [
      ['namespace-members-archived', 'e2e-dsh-api-archived-fixture', 'members', '已归档的命名空间'],
      ['namespace-members-readonly', 'team-2', 'members', '当前角色为成员'],
      ['namespace-members-frozen', 'frozen-team', 'members', '已冻结的命名空间'],
      ['namespace-global', 'global', 'skills', ''],
      ['namespace-catalog', 'catalog', 'skills', '']]) {
      await page.getByRole('button', { name: '我的命名空间', exact: true }).click();
      await page.getByRole('list', { name: '所属命名空间' }).getByRole('listitem').filter({ hasText: `@${slug} ·` }).getByRole('button', { name: '进入空间' }).click();
      if (section === 'members') {
        await page.getByRole('button', { name: '成员管理', exact: true }).click();
        await page.getByRole('list', { name: '空间成员', exact: true }).waitFor();
        const addMember = page.getByRole('button', { name: '添加成员', exact: true });
        assert(await addMember.isDisabled(), 'Read-only member management remains discoverable but disabled');
        const reasonId = await addMember.getAttribute('aria-describedby');
        assert(reasonId && await page.locator(`[id="${reasonId}"]`).isVisible(), 'Disabled action is associated with its visible permission reason');
        for (const name of ['修改角色', '移除']) {
          const button = page.locator('.astron-sh-namespace-member-row').filter({ hasText: '演示用户' }).getByRole('button', { name, exact: true });
          assert(await button.isVisible() && await button.isDisabled(), 'Read-only row action remains visible and disabled');
        }
        await addMember.evaluate(button => button.click());
        assert.equal(await dialog.count(), 0, 'Disabled member action cannot open a mutation dialog');
        assert(await page.getByRole('note').filter({ hasText: notice }).isVisible(), 'Restriction is explained');
        if (slug !== 'team-2') assert.equal(await page.locator('.astron-sh-namespace-heading').getByRole('button', { name: '上传技能', exact: true }).count(), 0, 'Inactive space has no upload action');
      } else {
        if (slug === 'global') await page.getByText('你还没有在此空间上传技能。', { exact: true }).waitFor();
        else await page.getByRole('list', { name: '我的上传', exact: true }).waitFor();
        assert.equal(await page.getByRole('tab', { name: '成员', exact: true }).count(), 0, 'Non-team space has no team management');
        if (slug === 'catalog') {
          assert.equal(await page.locator('.astron-sh-namespace').getByRole('button', { name: /上传/ }).count(), 0, 'Catalog has no upload action');
          assert(await page.getByRole('button', { name: '删除', exact: true }).isVisible(), 'Author delete remains available for service authorization');
        }
      }
      await pageLayout(name);
    }
    await page.reload();
    await page.getByRole('button', { name: toggleLabel(theme), exact: true }).waitFor();
    assert.equal(await page.evaluate(() => localStorage.getItem('skillhub-appearance')), theme, 'Preference survives reload');
    await page.close();
  }
  for (const width of [1280, 375]) for (const theme of ['light', 'dark']) {
    const page = await browser.newPage({ viewport: { width, height: 820 }, colorScheme: theme });
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(new URL('/persistence', server.url).href);
    await page.getByRole('button', { name: '账户菜单' }).click();
    await page.getByRole('menuitem', { name: '个人设置' }).click();
    await page.getByRole('button', { name: '更换个人 Token' }).click();
    await page.getByLabel('个人 API Token', { exact: true }).fill('invalid-storage-fixture');
    await page.getByRole('dialog').getByRole('button', { name: '登录', exact: true }).click();
    await page.getByRole('alert').filter({ hasText: '无法保存本机登录信息' }).waitFor();
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: '账户菜单' }).waitFor();
    assert((await page.evaluate(() => window.persistenceFixture.snapshot())).firstSaved, 'Failed replacement preserves the previous saved account');
    persistenceChecks++;
    await page.evaluate(() => window.persistenceFixture.seedPrivate());
    await page.getByRole('button', { name: '账户菜单' }).click();
    await page.getByRole('menuitem', { name: '断开连接', exact: true }).click();
    const retry = page.getByRole('button', { name: '清除保存的登录', exact: true });
    await retry.waitFor();
    await page.getByRole('button', { name: '登录', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '账户菜单' }).count(), 0);
    assert.equal((await page.evaluate(() => window.persistenceFixture.snapshot())).privateCache, false, 'Failed logout clears old private cache after revision remount');
    assert(await page.getByRole('alert').filter({ hasText: '重启后可能再次恢复' }).isVisible(), 'Cleanup warning survives the actual CatalogPage revision remount');
    assert(await retry.evaluate(el => { const r = el.getBoundingClientRect(); return r.left >= 0 && r.right <= innerWidth; }), 'Cleanup retry fits narrow layouts');
    persistenceChecks++;
    await page.evaluate(() => window.persistenceFixture.switchSource(true));
    await page.getByRole('button', { name: '账户菜单' }).waitFor();
    assert.equal(await retry.count(), 0, 'A different source does not expose the previous source cleanup command');
    assert((await page.evaluate(() => window.persistenceFixture.snapshot())).secondSaved);
    await page.evaluate(() => window.persistenceFixture.switchSource(false));
    await retry.waitFor();
    await page.evaluate(() => window.persistenceFixture.allowRemoval());
    await retry.click();
    await retry.waitFor({ state: 'hidden' });
    await page.getByRole('button', { name: '登录', exact: true }).waitFor();
    const cleared = await page.evaluate(() => window.persistenceFixture.snapshot());
    assert.equal(cleared.firstSaved, false); assert.equal(cleared.secondSaved, true);
    await page.evaluate(() => window.persistenceFixture.restart());
    await page.getByRole('button', { name: '登录', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '账户菜单' }).count(), 0, 'Simulated Host restart cannot revive the cleared fixture record');
    persistenceChecks++;
    await page.goto(new URL('/persistence?restore=1', server.url).href);
    await page.getByRole('alert').filter({ hasText: '暂时无法恢复本机登录信息' }).waitFor();
    const before = (await page.evaluate(() => window.persistenceFixture.snapshot())).identityCalls;
    await page.evaluate(() => { window.persistenceFixture.focus(false); window.persistenceFixture.focus(true); });
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    assert.equal((await page.evaluate(() => window.persistenceFixture.snapshot())).identityCalls, before, 'Window focus must not repeat failed decryption');
    await page.evaluate(() => { window.dispatchEvent(new Event('offline')); window.dispatchEvent(new Event('online')); });
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    assert.equal((await page.evaluate(() => window.persistenceFixture.snapshot())).identityCalls, before, 'Network reconnection must not repeat failed decryption');
    await page.evaluate(() => window.persistenceFixture.restoreAvailable());
    await page.getByRole('button', { name: '重新连接', exact: true }).click();
    await page.getByRole('button', { name: '账户菜单' }).waitFor();
    const recovered = await page.evaluate(() => window.persistenceFixture.snapshot());
    assert.notEqual(recovered.revision, 'fixture-1');
    assert.equal(recovered.identityCalls, before + 2, 'Recovery rechecks identity under the new source revision');
    persistenceChecks++;
    for (const mode of ['restore', 'network']) {
      await page.goto(new URL(`/persistence?${mode}=1`, server.url).href);
      await page.getByRole('button', { name: '重新连接', exact: true }).waitFor();
      const calls = (await page.evaluate(() => window.persistenceFixture.snapshot())).identityCalls;
      if (mode === 'network') {
        await page.evaluate(() => { window.dispatchEvent(new Event('offline')); window.dispatchEvent(new Event('online')); });
        await page.waitForFunction(expected => window.persistenceFixture.snapshot().identityCalls > expected, calls);
      }
      await page.evaluate(() => window.persistenceFixture.allowRemoval());
      await page.getByRole('button', { name: '清除保存的登录', exact: true }).click();
      await page.getByRole('button', { name: '重新连接', exact: true }).waitFor({ state: 'hidden' });
      await page.getByRole('button', { name: '登录', exact: true }).waitFor();
      assert.equal((await page.evaluate(() => window.persistenceFixture.snapshot())).firstSaved, false, 'Failed identity verification must still allow local sign-out');
      await page.evaluate(() => window.persistenceFixture.restart());
      await page.getByRole('button', { name: '登录', exact: true }).waitFor();
      assert.equal(await page.getByRole('button', { name: '账户菜单' }).count(), 0);
      persistenceChecks++;
    }
    await page.goto(new URL('/persistence', server.url).href);
    await page.getByRole('button', { name: '账户菜单' }).click();
    await page.evaluate(() => window.persistenceFixture.seedPrivate());
    await page.context().setOffline(true);
    await page.getByRole('menuitem', { name: '断开连接', exact: true }).click();
    await retry.waitFor();
    assert.equal((await page.evaluate(() => window.persistenceFixture.snapshot())).privateCache, false);
    await page.evaluate(() => window.persistenceFixture.allowRemoval());
    await retry.click();
    await retry.waitFor({ state: 'hidden' });
    await page.getByRole('button', { name: '登录', exact: true }).waitFor();
    assert.equal((await page.evaluate(() => window.persistenceFixture.snapshot())).firstSaved, false, 'Offline sign-out can retry disk cleanup without returning online');
    await page.evaluate(() => window.persistenceFixture.restart());
    await page.getByRole('button', { name: '登录', exact: true }).waitFor();
    assert.equal(await page.getByRole('button', { name: '账户菜单' }).count(), 0);
    persistenceChecks++;
    await page.context().setOffline(false);
    await page.close();
  }
  assert.deepEqual(errors, [], 'No browser errors');
  const report = { passed: true, checks, namespaceChecks, persistenceChecks, widths: [1280, 563, 375], themes: ['light', 'dark'], sourceConfiguration: true, hostThemeChanges: true, themePersistence: true, portalAndEmbeddedContrast: true, fixtureOnly: true };
  if (process.env.DSH_PROBE_OUTPUT) await writeFile(`${process.env.DSH_PROBE_OUTPUT}/account-ui-result.json`, JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report));
} finally { await browser.close(); server.stop(true); }
