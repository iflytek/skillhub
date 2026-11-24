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
let namespaceChecks = 0;
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
    await page.getByRole('button', { name: '我的上传', exact: true }).click();
    await page.getByRole('list', { name: '我的上传', exact: true }).waitFor();
    await pageLayout('namespace-uploads');
    await page.getByRole('button', { name: '删除', exact: true }).click();
    await layout('delete');
    await dialog.getByRole('button', { name: '取消' }).click();
    await page.getByRole('tab', { name: '成员', exact: true }).click();
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
    await dialog.getByRole('button', { name: '确认添加' }).click();
    await page.waitForFunction(() => document.querySelector('[role="dialog"]')?.getAttribute('aria-busy') === 'true');
    assert(await search.isDisabled(), 'Search locked during member write');
    assert(await dialog.getByRole('combobox').isDisabled(), 'Role locked during member write');
    assert(await dialog.getByRole('button', { name: /候选用户/ }).isDisabled(), 'Candidate locked during member write');
    await dialog.waitFor({ state: 'hidden' });
    await page.getByRole('tab', { name: '技能', exact: true }).click();
    await page.getByText('你还没有在此空间上传技能。', { exact: true }).waitFor();
    assert.equal(await page.getByText('已添加成员 candidate', { exact: true }).count(), 0, 'Member feedback does not mask skill empty state');
    await pageLayout('namespace-uploads-empty');
    await page.getByRole('tab', { name: '成员', exact: true }).click();
    await page.getByRole('button', { name: '修改角色' }).first().click();
    await layout('member-role');
    await page.keyboard.press('Escape');
    await page.getByRole('button', { name: '移除', exact: true }).first().click();
    await layout('remove-member');
    await dialog.getByRole('button', { name: '取消' }).click();
    for (const [name, slug, section, notice] of [
      ['namespace-members-archived', 'e2e-dsh-api-archived-fixture', 'members', '已归档的命名空间'],
      ['namespace-members-readonly', 'team-2', 'members', '当前角色为成员'],
      ['namespace-members-frozen', 'frozen-team', 'members', '已冻结的命名空间'],
      ['namespace-global', 'global', 'skills', ''],
      ['namespace-catalog', 'catalog', 'skills', '']]) {
      await page.getByRole('button', { name: '我的命名空间', exact: true }).click();
      await page.getByRole('list', { name: '所属命名空间' }).getByRole('listitem').filter({ hasText: `@${slug} ·` }).getByRole('button', { name: '进入空间' }).click();
      if (section === 'members') {
        await page.getByRole('tab', { name: '成员', exact: true }).click();
        await page.getByRole('list', { name: '空间成员', exact: true }).waitFor();
        assert.equal(await page.getByRole('button', { name: '添加成员', exact: true }).count(), 0, 'Restricted members page exposes no write controls');
        assert.equal(await page.getByRole('button', { name: '修改角色', exact: true }).count(), 0);
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
  assert.deepEqual(errors, [], 'No browser errors');
  const report = { passed: true, checks, namespaceChecks, widths: [1280, 563, 375], themes: ['light', 'dark'], sourceConfiguration: true, hostThemeChanges: true, themePersistence: true, portalAndEmbeddedContrast: true, fixtureOnly: true };
  if (process.env.DSH_PROBE_OUTPUT) await writeFile(`${process.env.DSH_PROBE_OUTPUT}/account-ui-result.json`, JSON.stringify(report, null, 2));
  console.log(JSON.stringify(report));
} finally { await browser.close(); server.stop(true); }
