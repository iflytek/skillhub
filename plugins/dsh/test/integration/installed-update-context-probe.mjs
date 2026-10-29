import assert from 'node:assert/strict';
/** Run against installed-update-context-component.tsx bundled for a browser. */
export async function probeInstalledUpdateContext(page) {
  const check = page.getByRole('button', { name: '检查更新', exact: true });
  const body = () => page.locator('body').innerText();
  const scan = async () => { await check.click(); await page.getByText('已检查 4/4 · 4 个技能可更新', { exact: true }).waitFor(); };
  await check.waitFor(); await scan();
  await page.evaluate(() => window.fixture.inventory());
  await page.waitForFunction(() => [...document.querySelectorAll('.astron-sh-installed-card')].filter(card => card.textContent.includes('可更新至')).length === 3);
  assert(!(await page.locator('.astron-sh-installed-card').filter({ hasText: '@demo/one' }).innerText()).includes('可更新至'));
  for (const change of ['source', 'session']) {
    await scan();
    await page.evaluate(change => window.fixture[change]('B'), change);
    await page.waitForFunction(() => !document.querySelector('.skillhub-installed-summary')?.textContent.includes('已检查'));
  }
  await scan();
  await page.evaluate(() => window.fixture.workspace('B'));
  await page.waitForFunction(() => !document.querySelector('.skillhub-installed-summary')?.textContent.includes('已检查'));
  await scan();
  await page.locator('.astron-sh-installed-card').first().getByRole('button', { name: /^查看 .* 详情$/ }).click();
  const detail = page.getByRole('region', { name: '已安装技能详情', exact: true });
  await detail.getByRole('button', { name: '更新', exact: true }).waitFor();
  await detail.getByRole('button', { name: '更新', exact: true }).click();
  await page.waitForFunction(() => window.fixture.begins().length === 1);
  assert.deepEqual(await page.evaluate(() => window.fixture.begins()), ['B']);
  assert(!(await detail.innerText()).includes('安装记录暂不可用'));
  await detail.getByRole('button', { name: '← 返回已安装', exact: true }).click();
  await page.evaluate(() => window.fixture.hold());
  await check.click();
  await page.waitForFunction(() => window.fixture.calls().length === 3);
  // Both context transitions happen before React can commit; generation must still advance twice.
  await page.evaluate(() => { window.fixture.workspace('C'); window.fixture.workspace('B'); window.fixture.release(); });
  await page.waitForFunction(() => !document.querySelector('.skillhub-installed-summary')?.textContent.includes('已检查'));
  assert.equal(await page.evaluate(() => window.fixture.calls().length), 3);
  assert(!(await body()).includes('可更新至'));
  for (const change of ['source', 'session']) {
    await page.evaluate(() => window.fixture.hold());
    await check.click(); await page.waitForFunction(() => window.fixture.calls().length === 3);
    await page.evaluate(change => window.fixture[change]('C'), change);
    await page.waitForFunction(() => !document.querySelector('.skillhub-installed-summary')?.textContent.match(/已检查|检查中/));
    await page.evaluate(() => window.fixture.release());
    assert.equal(await page.evaluate(() => window.fixture.calls().length), 3);
    assert(!(await body()).includes('可更新至'));
  }
  await page.evaluate(() => window.fixture.staleRound());
  await check.click();
  await page.waitForFunction(() => window.fixture.calls().length === 4);
  await page.getByText('可更新至 2', { exact: true }).waitFor();
  await page.evaluate(() => window.fixture.release());
  await page.getByText('已检查 8/8 · 8 处检查失败', { exact: true }).waitFor();
  assert.equal(await page.evaluate(() => window.fixture.calls().length), 4);
  assert(!(await body()).includes('可更新至'));
  return { passed: true, staleRoundClearsResultsAndStopsQueue: true, inventoryReplacement: true, sourceAndSessionReset: true, workspaceReset: true, workspaceDetailRestored: true, workspaceABALateReplies: true, sourceAndSessionLateReplies: true };
}
