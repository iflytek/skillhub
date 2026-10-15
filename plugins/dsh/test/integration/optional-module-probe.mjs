import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';

const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_FIXTURE_MODULE_STATE: state } = process.env;
if (!entry || !output || !state) throw new Error('Set isolated Host, output and module fixture state');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1280, height: 900 } });
const errors = [];
let writes = 0;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => {
  if (/\/(startInstall|executePlan|recoverOperation)$/.test(new URL(request.url()).pathname)) writes++;
});
try {
  await writeFile(state, 'unavailable');
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  await page.getByText('SkillHub', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('button', { name: /document-helper/ }).click();
  await plaza.getByText('评论是否可读取尚未确认，不代表没有内容。', { exact: true }).waitFor();
  assert.equal(await plaza.getByRole('tab', { name: '评论', exact: true }).count(), 0);
  await page.screenshot({ path: `${output}/module-unconfirmed.png` });
  await writeFile(state, 'available');
  await plaza.getByRole('button', { name: '重新检查可选内容', exact: true }).click();
  await plaza.getByRole('tab', { name: '评论', exact: true }).waitFor();
  await plaza.getByRole('button', { name: '重新检查可选内容', exact: true }).waitFor({ state: 'hidden' });
  await plaza.getByRole('tab', { name: '评论', exact: true }).click();
  await plaza.getByText('适合整理长文档', { exact: true }).waitFor();
  await page.screenshot({ path: `${output}/module-available.png` });
  assert.equal(writes, 0);
  assert.deepEqual(errors, []);
  const result = { passed: true, unavailableNotEmpty: true, unknownAutoTabHidden: true,
    explicitRetryRecovered: true, commentsLoadedAfterActivation: true, writes, errors };
  await writeFile(`${output}/module-result.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/module-failure.png` });
  console.log(JSON.stringify({ passed: false, error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors }));
  process.exitCode = 1;
} finally {
  await writeFile(state, 'available');
  await browser.close();
}
