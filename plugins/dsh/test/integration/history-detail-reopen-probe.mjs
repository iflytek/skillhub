import { chromium } from 'playwright';
import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';

const entry = process.env.DSH_PROBE_URL;
const output = process.env.DSH_PROBE_OUTPUT;
const title = process.env.DSH_SEARCH_FIXTURE_TITLE;
if (!entry || !output || !title) throw new Error('Set isolated DSH_PROBE_URL, DSH_PROBE_OUTPUT and DSH_SEARCH_FIXTURE_TITLE after restarting the Host');
const browser = await chromium.launch({ headless: true,
  ...(process.env.DSH_PROBE_CHROMIUM ? { executablePath: process.env.DSH_PROBE_CHROMIUM } : {}),
});
const page = await browser.newPage({ viewport: { width: 1440, height: 1000 }, locale: 'en-US' });
const requests = [];
const errors = [];
page.on('request', request => requests.push(new URL(request.url()).pathname.split('/').at(-1)));
page.on('pageerror', error => errors.push(error.name));
const responseFor = method => page.waitForResponse(response => new URL(response.url()).pathname.endsWith(`/${method}`));
try {
  await page.addLocatorHandler(page.getByRole('button', { name: 'Configure later', exact: true }), locator => locator.click());
  await page.addLocatorHandler(page.getByRole('button', { name: 'Continue', exact: true }), locator => locator.click());
  await page.goto(entry, { waitUntil: 'domcontentloaded' });
  const session = page.getByText(title, { exact: true });
  if (!await session.isVisible()) await page.getByText(process.env.DSH_SEARCH_FIXTURE_WORKSPACE ?? 'project', { exact: true }).click();
  await session.click();
  const card = page.getByRole('region', { name: 'SkillHub 搜索结果' });
  if (!await card.isVisible()) {
    const process = page.locator('button[data-turn-process="1"]');
    await process.waitFor();
    if (await process.getAttribute('aria-expanded') === 'false') await process.click();
  }
  if (!await card.isVisible()) await page.getByText('Called tools', { exact: true }).click();
  await card.waitFor();
  const originalCard = await card.innerText();
  assert.equal(requests.includes('reopenConversationDetail'), false);
  assert.equal(requests.includes('openConversationDetail'), false);
  const detailButton = card.getByRole('button', { name: '查看技能详情', exact: true }).first();
  const opened = responseFor('openConversationDetail');
  await detailButton.click();
  const originalRequest = (await opened).request().postDataJSON().payload.args;
  const drawer = page.getByRole('dialog', { name: '技能详情', exact: true });
  const reopenButton = drawer.getByRole('button', { name: '重新查看详情', exact: true });
  await reopenButton.waitFor();
  assert.equal(requests.includes('reopenConversationDetail'), false);
  assert.equal(await drawer.getByRole('button', { name: '重试', exact: true }).count(), 0);
  const reopened = responseFor('reopenConversationDetail');
  const acknowledged = responseFor('acknowledgeConversationDetail');
  await reopenButton.click();
  const freshResponse = await reopened;
  const freshRequest = freshResponse.request().postDataJSON().payload.args;
  const fresh = (await freshResponse.json()).result;
  assert.equal(fresh.ok, true);
  assert.equal(freshRequest.selectionRef, originalRequest.selectionRef);
  assert.ok(freshRequest.callId);
  assert.notEqual(fresh.value.selectionRef, originalRequest.selectionRef);
  const acknowledgment = await acknowledged;
  assert.equal(acknowledgment.request().postDataJSON().payload.args.selectionRef, fresh.value.selectionRef);
  assert.equal(acknowledgment.request().postDataJSON().payload.args.detailToken, fresh.value.detailToken);
  assert.equal((await acknowledgment.json()).result.value, true);
  await drawer.getByRole('heading', { name: fresh.value.title, exact: true }).waitFor();
  assert.ok((await drawer.innerText()).includes(fresh.value.version));
  assert.equal(await drawer.getByText(/^原搜索来源：/).isVisible(), false);
  assert.equal(await drawer.getByText(/^当前详情来源：/).isVisible(), false);
  await drawer.locator('summary').filter({ hasText: /^来源信息$/ }).click();
  await drawer.getByText(/^原搜索来源：/).waitFor();
  await drawer.getByText(/^当前详情来源：/).waitFor();
  await drawer.getByRole('combobox', { name: '安装范围', exact: true }).waitFor();
  await page.screenshot({ path: `${output}/history-detail-reopen.png` });
  await drawer.getByRole('button', { name: '关闭技能详情', exact: true }).click();
  assert.equal(await detailButton.evaluate(element => element === document.activeElement), true);
  assert.equal(await card.innerText(), originalCard);
  for (const mutation of ['startInstall', 'executePlan', 'recoverOperation']) assert.equal(requests.includes(mutation), false);
  assert.deepEqual(errors, []);
  await writeFile(`${output}/history-detail-reopen-result.json`, JSON.stringify({
    explicitReopen: true, staleHasNoRetry: true, newReferenceAcknowledged: true, originalCardPreserved: true,
    sourceAndVersionShown: true, installControlAvailable: true, focusRestored: true, noInstallationSubmission: true, pageErrors: errors,
  }, null, 2));
} finally { await browser.close(); }
