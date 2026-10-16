import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output } = process.env;
if (!entry || !output) throw new Error('Set isolated Host URL and output directory');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage();
const errors = [];
let writes = 0;
let reason = 'ANONYMOUS_DENIED';
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => { if (/\/(startInstall|executePlan)$/.test(new URL(request.url()).pathname)) writes++; });
await page.route('**/listCatalog', async route => {
  const response = await route.fetch();
  const body = await response.json();
  assert.equal(body.result.ok, true);
  body.result = { ok: false, error: { code: 'skillhub/registry-error', message: 'private diagnostic', details: { reason } } };
  await route.fulfill({ response, json: body });
});
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const welcome = page.getByRole('button', { name: /^(Continue|继续)$/ });
  const later = page.getByRole('button', { name: /^(Configure later|稍后配置)$/ });
  await welcome.or(later).first().waitFor();
  if (await welcome.isVisible()) await welcome.click();
  await later.click();
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  const cases = [
    ['ANONYMOUS_DENIED', '当前来源不允许匿名读取此项信息，请联系实例管理员确认公开访问配置。'],
    ['RATE_LIMITED', 'SkillHub 来源暂时限流，请稍后重试。'],
    ['NETWORK_ERROR', '无法连接 SkillHub 或请求超时，请检查网络后重试。'],
    ['REGISTRY_UNAVAILABLE', 'SkillHub 服务暂时不可用，请稍后重试。'],
    ['INVALID_RESPONSE', 'SkillHub 返回的数据格式不符合接口约定，请联系实例管理员检查兼容性。'],
  ];
  for (const [code, message] of cases) {
    if (reason !== code) {
      reason = code;
      await plaza.getByRole('button', { name: '重试', exact: true }).click();
    }
    await plaza.getByRole('alert').getByText(message, { exact: true }).waitFor();
    assert.equal(await plaza.getByText('private diagnostic', { exact: true }).count(), 0);
    assert.equal(await plaza.getByText('没有找到匹配的技能，请尝试其他关键词。', { exact: true }).count(), 0);
  }
  await page.screenshot({ path: `${output}/catalog-error.png` });
  await page.unroute('**/listCatalog');
  await plaza.getByRole('button', { name: '重试', exact: true }).click();
  await plaza.getByRole('button', { name: /document-helper/ }).waitFor();
  assert.equal(writes, 0); assert.deepEqual(errors, []);
  await writeFile(`${output}/catalog-error-result.json`, JSON.stringify({ passed: true, injectedRemoteErrors: cases.map(([code]) => code), recoveryToRealList: true, writes, errors }, null, 2));
} finally { await browser.close(); }
