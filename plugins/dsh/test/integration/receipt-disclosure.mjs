/** Reveal a successful historical receipt through its real disclosure controls. */
export async function revealReceipt(locator) {
  await locator.waitFor({ state: 'attached' });
  const ancestors = await locator.locator('xpath=ancestor::details').all();
  for (const ancestor of ancestors) {
    if (await ancestor.getAttribute('open') === null) await ancestor.locator(':scope > summary').click();
  }
  await locator.waitFor({ state: 'visible' });
}

/** Open the compact install receipt without changing its operation or query state. */
export async function expandInstallRecord(container) {
  const record = container.locator('details.astron-sh-install-record');
  await record.waitFor({ state: 'attached' });
  if (await record.getAttribute('open') === null) await record.locator(':scope > summary').click();
}
