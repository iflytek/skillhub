import crypto from 'node:crypto'
import { expect, test, type APIResponse, type Page } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'
import { registerSession } from './helpers/session'
import { E2eTestDataBuilder } from './helpers/test-data-builder'
import { csrfHeaders } from './helpers/csrf'

/**
 * End-to-end coverage for the skill authoring workbench against the real API:
 * draft creation, file editing (text + binary assets), runtime binding,
 * validation runs with the fix-preview loop, and the submit gate. Assertions
 * ride on durable UI state (button enablement, badges, response bodies) rather
 * than toasts so they do not race the toast auto-dismiss.
 */

interface ApiEnvelope<T> {
  code: number
  msg?: string
  data: T
}

interface DraftSummary {
  id: number
  name: string
}

interface DraftFileSummaryPayload {
  path: string
  sha256: string
  size: number
  contentType?: string
}

// Minimal 1×1 transparent PNG used for the binary-asset upload test.
const PNG_BUFFER = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==',
  'base64',
)

function uniqueSkillName(prefix: string): string {
  return `${prefix}-${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`
}

function validSkillMd(name: string): string {
  return `---
name: ${name}
description: greets the caller with a fixed message
---

# ${name}

## Overview

greets the caller with a fixed message
`
}

function brokenSkillMd(name: string): string {
  return `---
name: ${name}
---

# ${name}
`
}

const GREET_SCRIPT = 'echo hello from authoring e2e\n'

const VALIDATION_YAML = `version: 1
tasks:
  - name: greet
    description: script prints the greeting
    type: script
    script: scripts/greet.sh
    args: []
    timeoutMs: 30000
    assertions:
      - type: exit_code
        equals: 0
      - type: stdout_contains
        value: hello from authoring e2e
`

async function createDraftViaApi(page: Page, namespaceSlug: string, name: string): Promise<number> {
  const response = await page.context().request.post('/api/web/authoring/drafts', {
    data: { namespaceSlug, name },
    headers: await csrfHeaders(page),
  })
  const body = await response.json() as ApiEnvelope<DraftSummary>
  expect(body.code, `draft creation failed: ${body.msg ?? 'unknown error'}`).toBe(0)
  return body.data.id
}

async function deleteDraftViaApi(page: Page, draftId: number): Promise<void> {
  try {
    await page.context().request.delete(`/api/web/authoring/drafts/${draftId}`, {
      headers: await csrfHeaders(page),
    })
  } catch {
    // Best-effort cleanup for E2E environments.
  }
}

function waitForDraftFileRead(page: Page, path: string, draftId?: number): Promise<APIResponse> {
  // draftId is optional so the waiter can be registered BEFORE navigating to
  // the draft page — the editor fetches the selected file on mount, and a
  // waiter registered after navigation can miss the response entirely.
  return page.waitForResponse(
    (response) => {
      const url = response.url()
      return response.request().method() === 'GET'
        && url.includes('/api/web/authoring/drafts/')
        && url.includes('/files/content')
        && url.includes(`path=${encodeURIComponent(path)}`)
        && (draftId === undefined
          || url.includes(`/api/web/authoring/drafts/${draftId}/files/content`))
    },
  )
}

function waitForDraftFileSave(page: Page, draftId: number): Promise<APIResponse> {
  return page.waitForResponse(
    (response) =>
      response.request().method() === 'PUT'
      && response.url().includes(`/api/web/authoring/drafts/${draftId}/files`)
      && !response.url().includes('/runtime'),
  )
}

/** Creates a text file through the workbench UI and fills it with `content`. */
async function createFileViaUi(page: Page, draftId: number, path: string, content: string): Promise<void> {
  await page.locator('input[placeholder="scripts/run.py"]').fill(path)
  // register before the click: the editor swap triggers a content read of the
  // new (empty) file once the create PUT resolves, and waiting for that read
  // keeps the fill below from being overwritten by the fetch resolving late.
  const newFileRead = waitForDraftFileRead(page, path, draftId)
  await page.getByRole('button', { name: 'Create file' }).click()
  await newFileRead
  await expect(page.getByTestId('draft-file-list')).toContainText(path)
  const editor = page.getByTestId('file-editor')
  await editor.fill(content)
  const saveResponse = waitForDraftFileSave(page, draftId)
  await page.getByTestId('save-file').click()
  expect((await saveResponse).status()).toBe(200)
}

test.describe('Authoring Flow UI (Real API)', () => {
  test.beforeEach(async ({ page }, testInfo) => {
    await setEnglishLocale(page)
    await registerSession(page, testInfo)
  })

  test('create → edit → bind → validate → submit through the workbench UI', async ({ page }, testInfo) => {
    test.setTimeout(180_000)
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()
    let draftId: number | undefined
    try {
      const namespace = await builder.ensureWritableNamespace()
      const skillName = uniqueSkillName('authoring-ui')

      // --- create the draft from the workbench list
      await page.goto('/dashboard/authoring')
      await expect(page.getByRole('heading', { name: 'Skill authoring' })).toBeVisible()
      await page.getByTestId('create-draft').click()
      const createDialog = page.getByRole('dialog')
      await expect(createDialog).toBeVisible()
      await createDialog.getByTestId('draft-namespace').click()
      const namespaceOption = page.getByRole('option', { name: namespace.displayName }).first()
      await namespaceOption.evaluate((element) => {
        element.scrollIntoView({ block: 'center' })
        element.click()
      })
      await expect(createDialog.getByTestId('draft-namespace')).toContainText(namespace.displayName)
      await createDialog.getByTestId('draft-name').fill(skillName)
      await createDialog.getByTestId('submit-create-draft').click()

      const draftLink = page.getByRole('link', { name: skillName, exact: true })
      await expect(draftLink).toBeVisible({ timeout: 15_000 })
      const skillMdRead = waitForDraftFileRead(page, 'SKILL.md')
      await draftLink.click()
      await expect(page.getByRole('heading', { name: skillName })).toBeVisible()
      draftId = Number(page.url().split('/').pop())
      expect(Number.isFinite(draftId)).toBeTruthy()

      // --- files tab: replace the scaffold, add the script and validation spec
      const editor = page.getByTestId('file-editor')
      await expect(editor).toBeVisible()
      // the freshly created draft serves its scaffold immediately (regression
      // guard: creation must persist content, not just the metadata row)
      expect((await skillMdRead).status()).toBe(200)
      await expect(editor).toHaveValue(/name: /)
      await editor.fill(validSkillMd(skillName))
      const skillMdSave = waitForDraftFileSave(page, draftId)
      await page.getByTestId('save-file').click()
      expect((await skillMdSave).status()).toBe(200)

      await createFileViaUi(page, draftId, 'scripts/greet.sh', GREET_SCRIPT)
      await createFileViaUi(page, draftId, 'validation.yaml', VALIDATION_YAML)
      await expect(page.getByTestId('draft-file-list')).toContainText('SKILL.md')

      // --- runtime binding tab: keep the default local-script/sh binding
      await page.getByRole('tab', { name: 'Runtime binding' }).click()
      await expect(page.getByTestId('agent-type')).toBeVisible()
      await expect(page.locator('#interpreter')).toHaveValue('sh')
      const bindingSave = page.waitForResponse(
        (response) =>
          response.request().method() === 'PUT'
          && response.url().includes(`/api/web/authoring/drafts/${draftId}/runtime`),
      )
      await page.getByTestId('save-binding').click()
      expect((await bindingSave).status()).toBe(200)

      // --- validate: the run should pass all three layers
      await page.getByRole('tab', { name: 'Files' }).click()
      const runStart = page.waitForResponse(
        (response) =>
          response.request().method() === 'POST'
          && response.url().includes(`/api/web/authoring/drafts/${draftId}/runs`),
      )
      await page.getByTestId('start-run').click()
      expect((await runStart).status()).toBe(200)
      await expect(page.getByTestId('run-status')).toBeVisible({ timeout: 15_000 })
      await expect(page.getByTestId('run-status')).toHaveText('Succeeded', { timeout: 90_000 })
      await expect(page.getByTestId('error-count')).toHaveText('0')
      await expect(page.getByTestId('task-count')).toHaveText('1')
      // the behavior task's stdout lands in the event console
      await expect(page.getByText('hello from authoring e2e')).toBeVisible()

      // --- back to the draft and submit through the gated dialog
      await page.getByRole('link', { name: 'Back to draft' }).click()
      await expect(page.getByRole('heading', { name: skillName })).toBeVisible()
      await expect(page.getByText('Validated at revision')).toBeVisible({ timeout: 15_000 })
      const submitButton = page.getByTestId('submit-draft')
      await expect(submitButton).toBeEnabled()
      await submitButton.click()
      const submitDialog = page.getByTestId('submit-dialog')
      await expect(submitDialog).toBeVisible()
      const submitResponsePromise = page.waitForResponse(
        (response) =>
          response.request().method() === 'POST'
          && response.url().includes(`/api/web/authoring/drafts/${draftId}/submit`),
        { timeout: 120_000 },
      )
      await submitDialog.getByRole('button', { name: 'Submit', exact: true }).click()
      const submitResponse = await submitResponsePromise
      expect(submitResponse.status()).toBe(200)
      const submitBody = await submitResponse.json() as ApiEnvelope<{ skillId: number }>
      expect(submitBody.code, `submit failed: ${submitBody.msg ?? 'unknown error'}`).toBe(0)
      expect(submitBody.data.skillId).toBeTruthy()
      await expect(page.getByTestId('submit-draft')).toBeDisabled()
      await expect(page.getByTestId('submit-draft')).toContainText('Submitted')
    } finally {
      if (draftId !== undefined) {
        await deleteDraftViaApi(page, draftId)
      }
      await builder.cleanup()
    }
  })

  test('failing validation shows a previewable fix; apply → re-validate succeeds', async ({ page }, testInfo) => {
    test.setTimeout(180_000)
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()
    let draftId: number | undefined
    try {
      const namespace = await builder.ensureWritableNamespace()
      const skillName = uniqueSkillName('authoring-fix')
      draftId = await createDraftViaApi(page, namespace.slug, skillName)

      // break the scaffold: drop the required description field
      const skillMdRead = waitForDraftFileRead(page, 'SKILL.md', draftId)
      await page.goto(`/dashboard/authoring/${draftId}`)
      await expect(page.getByRole('heading', { name: skillName })).toBeVisible()
      const editor = page.getByTestId('file-editor')
      await expect(editor).toBeVisible()
      await skillMdRead
      await editor.fill(brokenSkillMd(skillName))
      const skillMdSave = waitForDraftFileSave(page, draftId)
      await page.getByTestId('save-file').click()
      expect((await skillMdSave).status()).toBe(200)

      // validate → fails with a finding that carries a suggested fix
      await page.getByTestId('start-run').click()
      await expect(page.getByTestId('run-status')).toBeVisible({ timeout: 15_000 })
      await expect(page.getByTestId('run-status')).toHaveText('Failed', { timeout: 90_000 })
      await expect(page.getByTestId('error-count')).not.toHaveText('0')
      const finding = page.locator('[data-testid^="finding-"]', {
        hasText: 'FRONTMATTER_FIELD_MISSING',
      }).first()
      await expect(finding).toBeVisible({ timeout: 15_000 })

      // preview the suggested patch before applying it
      await finding.getByTestId('suggestion-toggle').click()
      const diffPreview = finding.locator('pre')
      await expect(diffPreview).toBeVisible()
      await expect(diffPreview).toContainText('description:')

      // two-step confirm: apply, then watch the finding flip to Applied
      await finding.getByTestId('apply-start').click()
      await finding.getByTestId('apply-confirm').click()
      await expect(finding.getByTestId('finding-status')).toContainText('Applied', { timeout: 15_000 })

      // one click back to the workbench, re-validate, and the run passes
      await page.getByRole('button', { name: 'Fix and re-validate' }).click()
      await expect(page.getByRole('heading', { name: skillName })).toBeVisible()
      await page.getByTestId('start-run').click()
      await expect(page.getByTestId('run-status')).toBeVisible({ timeout: 15_000 })
      await expect(page.getByTestId('run-status')).toHaveText('Succeeded', { timeout: 90_000 })
      await expect(page.getByTestId('error-count')).toHaveText('0')
    } finally {
      if (draftId !== undefined) {
        await deleteDraftViaApi(page, draftId)
      }
      await builder.cleanup()
    }
  })

  test('uploads a binary asset that shows read-only with byte-accurate metadata', async ({ page }, testInfo) => {
    test.setTimeout(120_000)
    const builder = new E2eTestDataBuilder(page, testInfo)
    await builder.init()
    let draftId: number | undefined
    try {
      const namespace = await builder.ensureWritableNamespace()
      const skillName = uniqueSkillName('authoring-binary')
      draftId = await createDraftViaApi(page, namespace.slug, skillName)

      await page.goto(`/dashboard/authoring/${draftId}`)
      await expect(page.getByRole('heading', { name: skillName })).toBeVisible()

      await page.getByTestId('upload-file-button').click()
      const uploadDialog = page.getByRole('dialog')
      await expect(uploadDialog).toBeVisible()
      await uploadDialog.getByTestId('upload-file-input').setInputFiles({
        name: 'logo.png',
        mimeType: 'image/png',
        buffer: PNG_BUFFER,
      })
      // choosing a file prefills the target draft path
      await expect(uploadDialog.getByTestId('upload-path-input')).toHaveValue('assets/logo.png')
      const uploadSave = waitForDraftFileSave(page, draftId)
      await uploadDialog.getByTestId('confirm-upload').click()
      expect((await uploadSave).status()).toBe(200)

      // the asset appears in the file list and opens the read-only binary panel
      await page.getByTestId('draft-file-list').getByRole('button', { name: 'assets/logo.png', exact: true }).click()
      const panel = page.getByTestId('binary-file-panel')
      await expect(panel).toBeVisible()
      await expect(panel).toContainText('assets/logo.png')
      await expect(panel).toContainText('image/png')
      await expect(panel).toContainText(`${PNG_BUFFER.length} B`)
      await expect(panel.getByTestId('replace-file')).toBeVisible()
      await expect(panel.getByTestId('delete-file')).toBeVisible()

      // the stored bytes round-trip byte-accurately (sha256 over the API-reported content)
      const filesResponse = await page.context().request.get(`/api/web/authoring/drafts/${draftId}/files`)
      const files = await filesResponse.json() as ApiEnvelope<DraftFileSummaryPayload[]>
      const uploaded = files.data.find((file) => file.path === 'assets/logo.png')
      expect(uploaded?.size).toBe(PNG_BUFFER.length)
      expect(uploaded?.contentType).toBe('image/png')
      expect(uploaded?.sha256).toBe(crypto.createHash('sha256').update(PNG_BUFFER).digest('hex'))
    } finally {
      if (draftId !== undefined) {
        await deleteDraftViaApi(page, draftId)
      }
      await builder.cleanup()
    }
  })
})
