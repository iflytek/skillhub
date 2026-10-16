import { selectValue } from './select-field.mjs';
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from 'playwright';
const { DSH_PROBE_URL: entry, DSH_PROBE_OUTPUT: output, DSH_SEARCH_FIXTURE_TITLE: title,
  DSH_REGISTRY_SKILL_SLUG: slug, DSH_REGISTRY_SKILL_VERSION: version } = process.env;
if (!entry || !output || !title || !slug || !version) throw new Error('Set isolated Host, fresh session, public skill/version and output');
const browser = await chromium.launch({ headless: true, executablePath: process.env.DSH_PROBE_CHROMIUM });
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } });
const errors = []; let installs = 0;
page.on('pageerror', error => errors.push(error.message.replace(/token=[^\s&]+/g, 'token=[redacted]')));
page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/startInstall')) installs++; });
try {
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'Configure later', exact: true }).click();
  await page.getByText(title, { exact: true }).click();
  await page.getByText('技能中心', { exact: true }).click();
  const plaza = page.getByRole('region', { name: 'SkillHub 技能广场' });
  await plaza.getByRole('textbox', { name: '搜索技能', exact: true }).fill(slug);
  await plaza.getByRole('button', { name: '搜索', exact: true }).click();
  await plaza.getByRole('button').filter({ has: page.getByText(slug, { exact: true }) }).click();
  await plaza.getByRole('heading', { name: `安装 ${version}`, exact: true }).waitFor();
  await plaza.getByRole('tab', { name: '版本', exact: true }).click();
  await plaza.getByRole('navigation', { name: '版本分页' }).waitFor();
  await plaza.getByRole('tab', { name: '评论', exact: true }).click();
  await plaza.getByRole('navigation', { name: '评论分页' }).waitFor();
  await plaza.getByRole('tab', { name: '详情', exact: true }).click();
  await selectValue(plaza.getByRole('combobox', { name: '安装范围', exact: true }), 'project');
  await plaza.getByRole('button', { name: '安装到当前项目', exact: true }).click();
  await page.getByRole('dialog', { name: '确认安装技能？', exact: true }).getByRole('button', { name: '确认安装', exact: true }).click();
  await plaza.getByText('已安装此版本', { exact: true }).waitFor();
  await page.screenshot({ path: `${output}/live-registry-installed.png` });
  assert.equal(installs, 1); assert.deepEqual(errors, []);
  const result = { passed: true, slug, version, anonymousBrowseAndModules: true, projectInstallation: true, installs, errors };
  await writeFile(`${output}/live-registry-result.json`, JSON.stringify(result, null, 2)); console.log(JSON.stringify(result));
} catch (error) {
  await page.screenshot({ path: `${output}/live-registry-failure.png` });
  console.log(JSON.stringify({ error: error.message.replace(/token=[^\s&]+/g, 'token=[redacted]'), errors })); process.exitCode = 1;
} finally { await browser.close(); }
