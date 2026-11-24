import assert from 'node:assert/strict';
import { chromium } from 'playwright';
import { writeFile } from 'node:fs/promises';
import { build as buildFixture, serve } from 'bun';

// Run with Bun. This probe owns its fixture server and tests actual account components.
const build = await buildFixture({ entrypoints: [new URL('./account-ui-fixture.tsx', import.meta.url).pathname], target: 'browser', define: { 'process.env.NODE_ENV': '"development"' } });
assert(build.success, 'Fixture must build');
const script = await build.outputs[0].text();
const server = serve({ hostname: '127.0.0.1', port: 0, fetch: request => new URL(request.url).pathname === '/app.js'
  ? new Response(script, { headers: { 'Content-Type': 'text/javascript' } })
  : new Response('<!doctype html><html style="color-scheme:light dark"><meta name="viewport" content="width=device-width"><div id="root"></div><script type="module" src="/app.js"></script></html>', { headers: { 'Content-Type': 'text/html' } }) });
const browser = await chromium.launch({ headless: true, ...(process.env.DSH_PROBE_CHROMIUM ? { executablePath: process.env.DSH_PROBE_CHROMIUM } : {}) });
const errors = [];
let checks = 0;
try {
  for (const width of [1280, 563, 375]) for (const theme of ['light', 'dark']) {
    const page = await browser.newPage({ viewport: { width, height: 820 }, colorScheme: theme });
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(server.url.href);
    const dialog = page.getByRole('dialog');
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
    await dialog.getByLabel('技能包', { exact: false }).setInputFiles({ name: 'fixture.zip', mimeType: 'application/zip', buffer: Buffer.from('fixture-package') });
    await dialog.getByRole('button', { name: '核对上传信息' }).click();
    await dialog.getByRole('region', { name: '确认上传目标' }).waitFor();
    await layout('upload-confirm');
    await dialog.getByRole('button', { name: '确认上传' }).click();
    await dialog.getByRole('heading', { name: '已上传' }).waitFor();
    await layout('upload-receipt');
    await dialog.getByRole('button', { name: '关闭', exact: true }).click();
    await page.waitForFunction(() => document.body.style.pointerEvents !== 'none');
    await page.getByRole('button', { name: '账户菜单' }).click();
    await page.getByRole('menuitem', { name: '我的命名空间' }).click();
    await page.getByRole('button', { name: '进入空间' }).first().click();
    await page.getByRole('button', { name: '删除', exact: true }).click();
    await layout('delete');
    await dialog.getByRole('button', { name: '取消' }).click();
    await page.getByRole('button', { name: '成员', exact: true }).click();
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
    await dialog.getByRole('button', { name: '确认添加' }).click();
    await page.waitForFunction(() => document.querySelector('[role="dialog"]')?.getAttribute('aria-busy') === 'true');
    assert(await search.isDisabled(), 'Search locked during member write');
    assert(await dialog.getByRole('combobox').isDisabled(), 'Role locked during member write');
    assert(await dialog.getByRole('button', { name: /候选用户/ }).isDisabled(), 'Candidate locked during member write');
    await dialog.waitFor({ state: 'hidden' });
    await page.getByRole('button', { name: '修改角色' }).click();
    await layout('member-role');
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: '移除', exact: true }).click();
    await layout('remove-member');
    await dialog.getByRole('button', { name: '取消' }).click();
    await page.close();
  }
  assert.deepEqual(errors, [], 'No browser errors');
  const report = { passed: true, checks, widths: [1280, 563, 375], themes: ['light', 'dark'], fixtureOnly: true };
  if (process.env.DSH_PROBE_OUTPUT) await writeFile(`${process.env.DSH_PROBE_OUTPUT}/account-ui-result.json`, JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report));
} finally { await browser.close(); server.stop(true); }
