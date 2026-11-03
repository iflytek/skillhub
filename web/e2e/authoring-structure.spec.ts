import { expect, test } from '@playwright/test'
import { setEnglishLocale, setUniqueClientIp } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'
import { csrfHeaders } from './helpers/csrf'

test('authoring routes require a session', async ({ page }) => {
  await setEnglishLocale(page)
  await page.goto('/dashboard/authoring')
  await expect(page).toHaveURL(/\/login\?returnTo=/)
})

test('creates a draft, edits files and rechecks structure using merged APIs', async ({ page }, testInfo) => {
  await setEnglishLocale(page)
  await setUniqueClientIp(page, testInfo.title)
  await registerSession(page, testInfo, { allowMockSession: false })
  const builder = new E2eTestDataBuilder(page, testInfo)
  await builder.init()
  const namespace = await builder.ensureWritableNamespace()
  const name = `structure-${Date.now().toString(36)}`
  let draftId: number | undefined
  const authoringRequests: string[] = []
  page.on('request', request => { if (request.url().includes('/authoring/')) authoringRequests.push(request.url()) })
  try {
    await page.goto('/dashboard/authoring')
    await page.getByTestId('create-draft').click()
    await page.getByTestId('draft-namespace').click()
    await page.getByRole('option', { name: namespace.displayName || namespace.slug, exact: true }).click()
    await page.getByLabel('Skill name', { exact: true }).fill(name)
    const createdResponse = page.waitForResponse(response => response.url().endsWith('/api/web/authoring/drafts') && response.request().method() === 'POST')
    await page.getByTestId('submit-create-draft').click()
    const created = await (await createdResponse).json()
    draftId = created.data.id
    expect(draftId).toBeDefined()
    await page.getByRole('link', { name, exact: true }).click()
    await expect(page.getByRole('heading', { name, exact: true })).toBeVisible()
    const editor = page.getByLabel('File content', { exact: true })
    await expect(editor).toBeVisible()
    await editor.fill(`---\nname: ${name}\n---\n\n# Structure test\n`)
    await expect(page.getByTestId('start-run')).toBeDisabled()
    await page.getByRole('button', { name: 'Save', exact: true }).click()
    await expect(page.getByTestId('start-run')).toBeEnabled()
    await page.getByTestId('start-run').click()
    await expect(page.getByTestId('validation-results').getByText(/Failed · revision/)).toBeVisible({ timeout: 30_000 })
    await expect(page.getByText(/FRONTMATTER_FIELD_MISSING/)).toBeVisible()
    await editor.fill(`---\nname: ${name}\ndescription: A structure validation test skill\n---\n\n# Structure test\n\nUse this skill to test package validation.\n`)
    await page.getByRole('button', { name: 'Save', exact: true }).click()
    await expect(page.getByTestId('start-run')).toBeEnabled()
    await page.getByTestId('start-run').click()
    await expect(page.getByTestId('validation-results').getByText(/Succeeded · revision/)).toBeVisible({ timeout: 30_000 })
    await expect(page.getByText(/Structure checked/)).toBeVisible()
    // A successful structural check must not introduce runtime, fix or publish actions.
    expect(authoringRequests.some(url => /\/(runtime|submit|apply|dismiss)(?:\?|$)/.test(url))).toBe(false)
    const persisted = await page.request.get(`/api/web/authoring/drafts/${draftId}`)
    expect((await persisted.json()).data.validated).toBe(true)

    // Transport encoding, rather than filename guessing, keeps binary bytes out of the editor.
    const png = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg==', 'base64')
    await page.getByLabel('Path', { exact: true }).fill('assets/pixel.png')
    await page.getByLabel('Upload file', { exact: true }).setInputFiles({ name: 'pixel.png', mimeType: 'image/png', buffer: png })
    await page.getByRole('button', { name: 'Upload', exact: true }).click()
    await expect(page.getByTestId('binary-file-panel')).toBeVisible()
    const binary = await page.request.get(`/api/web/authoring/drafts/${draftId}/files/content?path=assets%2Fpixel.png`)
    const binaryFile = (await binary.json()).data
    expect(binaryFile.encoding).toBe('base64')
    expect(Buffer.from(binaryFile.content, 'base64').equals(png)).toBe(true)
    const invalidated = await page.request.get(`/api/web/authoring/drafts/${draftId}`)
    expect((await invalidated.json()).data.validated).toBe(false)

    // Private draft and content are also refused by the real API without this session.
    const stranger = await page.context().browser()!.newContext({ baseURL: testInfo.project.use.baseURL })
    try {
      expect((await stranger.request.get(`/api/web/authoring/drafts/${draftId}`)).status()).toBe(401)
      expect((await stranger.request.get(`/api/web/authoring/drafts/${draftId}/files/content?path=SKILL.md`)).status()).toBe(401)
    } finally { await stranger.close() }

    for (const [theme, width, density] of [['light', 1440, 'regular'], ['dark', 390, 'compact']] as const) {
      await page.setViewportSize({ width, height: 900 })
      await page.evaluate(({ theme, density }) => {
        document.documentElement.classList.toggle('dark', theme === 'dark')
        document.documentElement.dataset.density = density
      }, { theme, density })
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
      await page.screenshot({ path: testInfo.outputPath(`structure-${theme}-${width}.png`), fullPage: true })
    }
  } finally {
    if (draftId != null) {
      const removed = await page.request.delete(`/api/web/authoring/drafts/${draftId}`, { headers: await csrfHeaders(page) })
      expect(removed.ok()).toBeTruthy()
    }
    await builder.cleanup()
  }
})
