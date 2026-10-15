/** Component-only browser probe. Uses React 18; does not claim Host/API acceptance.
 * DSH_PROBE_REACT_DOM: absolute path to an isolated react-dom/client.js (18.3.1).
 * DSH_PROBE_OUTPUT: existing output directory; DSH_PROBE_CHROMIUM: optional browser.
 * Run with bun from plugins/dsh. No model calls or installed skill changes.
 */
import assert from 'node:assert/strict';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'bun';
import { chromium } from 'playwright';
const { DSH_PROBE_REACT_DOM: renderer, DSH_PROBE_OUTPUT: output } = process.env;
assert(renderer && output, 'Set isolated ReactDOM client path and output directory');
assert.equal(JSON.parse(await readFile(resolve(dirname(renderer), 'package.json'), 'utf8')).version, '18.3.1');
const component = fileURLToPath(new URL('../../src/client/operation-details.tsx', import.meta.url));
const react = fileURLToPath(new URL('../../node_modules/react/', import.meta.url));
const bundle = await build({ entrypoints: ['operation-details-fixture'], target: 'browser', format: 'iife',
  plugins: [{ name: 'isolated-render-fixture', setup(builder) {
    builder.onResolve({ filter: /^operation-details-fixture$/ }, () => ({ path: 'entry', namespace: 'fixture' }));
    builder.onResolve({ filter: /^react(\/jsx(-dev)?-runtime)?$/ }, ({ path }) => ({ path: resolve(react, path === 'react' ? 'index.js' : path.slice(6) + '.js') }));
    builder.onLoad({ filter: /.*/, namespace: 'fixture' }, () => ({ loader: 'jsx', contents: `
      import { createRoot } from ${JSON.stringify(renderer)};
      import { OperationDetails } from ${JSON.stringify(component)};
      const root = createRoot(document.getElementById('root'));
      window.renderOperation = operation => root.render(<OperationDetails operation={operation}/>);
    ` }));
  } }] });
assert(bundle.success, String(bundle.logs));
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
const errors = [];
page.on('pageerror', error => errors.push(error.message));
const checks = [];
try {
  await page.setContent('<!doctype html><html lang="zh"><meta charset="utf-8"><title>操作结果组件验证</title><body><main id="root"></main></body></html>');
  await page.addScriptTag({ content: await bundle.outputs[0].text() });
  const render = async operation => {
    await page.evaluate(operation => window.renderOperation(operation), operation);
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  };
  await render({});
  assert.equal(await page.locator('details').count(), 0);
  checks.push('legacy response without resultDetails renders no fabricated history');
  await render({ resultDetails: { beforeVersion: '1.2.0', afterVersion: '1.3.0',
    targets: [{ label: '目标 1（确认计划顺序）', outcome: 'upgraded' }, { label: '目标 2（确认计划顺序）', outcome: 'upgraded' }], warnings: [] } });
  const summary = page.locator('summary');
  await summary.focus(); await page.keyboard.press('Enter');
  await page.getByText('操作前版本：1.2.0', { exact: true }).waitFor();
  await page.getByText('操作后版本：1.3.0', { exact: true }).waitFor();
  assert.equal(await page.getByText(/目标 [12]（确认计划顺序）：升级完成/).count(), 2);
  await page.screenshot({ path: resolve(output, 'operation-details-dual-mobile.png') });
  checks.push('dual-target upgrade and versions; keyboard expand');
  await render({ resultDetails: { beforeVersion: null, afterVersion: null,
    targets: [{ label: '当前项目', outcome: 'unknown' }], warnings: ['backup-cleanup', 'removal-cleanup', 'verification-required'] } });
  await page.getByText('当前项目：结果待核验', { exact: true }).waitFor();
  await page.getByText('操作后版本：未确认或不适用', { exact: true }).waitFor();
  assert.equal(await page.getByRole('list', { name: '操作告警' }).getByRole('listitem').count(), 3);
  await page.getByText('以上为原操作记录，当前安装状态请查看已安装列表。', { exact: true }).waitFor();
  const dimensions = await page.locator('details').evaluate(element => ({ client: element.clientWidth, scroll: element.scrollWidth }));
  assert(dimensions.client > 0 && dimensions.scroll <= dimensions.client + 1);
  await page.screenshot({ path: resolve(output, 'operation-details-unknown-mobile.png') });
  checks.push('unknown recovery not reported successful; three safe warning categories; 390px fit');
  await render({ resultDetails: { beforeVersion: null, afterVersion: null, targets: [], warnings: [] } });
  await page.getByText('原日志未记录目标明细。', { exact: true }).waitFor();
  checks.push('missing historical targets remain explicitly unknown');
  await render({ resultDetails: { beforeVersion: '1.2.0', afterVersion: null,
    targets: [{ label: '当前项目', outcome: 'restored' }], warnings: [] } });
  await page.getByText('当前项目：已恢复', { exact: true }).waitFor();
  checks.push('restored target is distinct from completed upgrade');
  await render({ resultDetails: { beforeVersion: '1.2.0-' + 'a'.repeat(200), afterVersion: '1.3.0',
    targets: [{ label: '<img src=x onerror=alert(1)>', outcome: 'upgraded' }], warnings: [] } });
  assert.equal(await page.locator('details img').count(), 0);
  const longText = await page.locator('details').evaluate(element => ({ client: element.clientWidth, scroll: element.scrollWidth }));
  assert(longText.scroll <= longText.client + 1, 'Long version identifiers must wrap on mobile');
  checks.push('HTML-shaped label remains text and long version wraps');
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: resolve(output, 'operation-details-long-version-desktop.png') });
  await summary.focus(); await page.keyboard.press('Space');
  assert.equal(await page.locator('details').getAttribute('open'), null);
  checks.push('desktop render and keyboard collapse');
  assert.deepEqual(errors, []);
  await writeFile(resolve(output, 'operation-details-browser-result.json'), JSON.stringify({ passed: true,
    scope: 'actual React component with synthetic responses; no Host or API verification', reactDom: '18.3.1', viewport: 390, checks, dimensions, errors }, null, 2));
} finally { await browser.close(); }
