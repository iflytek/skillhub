import { expect, test, type Page, type Route } from '@playwright/test'
import { setEnglishLocale } from './helpers/auth-fixtures'

const CANARY = 'SYNTHETIC-CANARY-868'
const MASKED_SNIPPET = 'const token = "[REDACTED]"'

function envelope(data: unknown) {
  return {
    code: 0,
    msg: 'success',
    data,
    timestamp: '2026-09-17T00:00:00Z',
    requestId: 'security-audit-redaction-fixture',
  }
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify(envelope(data)),
  })
}

async function mockSkillDetail(page: Page, audits: unknown[]) {
  await page.route(/\/api\//, async (route) => {
    const url = new URL(route.request().url())
    const { pathname } = url

    if (!pathname.startsWith('/api/')) {
      await route.continue()
      return
    }

    if (pathname === '/api/v1/auth/me') {
      await fulfill(route, {
        userId: 'audit-admin',
        displayName: 'Audit Admin',
        platformRoles: ['SUPER_ADMIN'],
        oauthProvider: 'local',
        canChangePassword: true,
      })
      return
    }

    if (pathname === '/api/web/skills/global/redaction-skill') {
      await fulfill(route, {
        id: 868,
        slug: 'redaction-skill',
        displayName: 'Redaction Skill',
        ownerId: 'skill-owner',
        ownerDisplayName: 'Skill Owner',
        summary: 'Security audit redaction fixture',
        visibility: 'PUBLIC',
        status: 'ACTIVE',
        downloadCount: 0,
        starCount: 0,
        ratingCount: 0,
        hidden: false,
        namespace: 'global',
        canManageLifecycle: false,
        canSubmitPromotion: false,
        canInteract: false,
        canReport: false,
        headlineVersion: { id: 8681, version: '1.0.0', status: 'PUBLISHED' },
        publishedVersion: { id: 8681, version: '1.0.0', status: 'PUBLISHED' },
        resolutionMode: 'PUBLISHED',
        labels: [],
      })
      return
    }

    if (pathname === '/api/web/skills/global/redaction-skill/versions') {
      await fulfill(route, {
        items: [{
          id: 8681,
          version: '1.0.0',
          status: 'PUBLISHED',
          fileCount: 0,
          totalSize: 0,
          publishedAt: '2026-09-17T00:00:00Z',
          downloadAvailable: true,
        }],
        total: 1,
        page: 0,
        size: 20,
      })
      return
    }

    if (pathname === '/api/web/skills/global/redaction-skill/versions/1.0.0/files') {
      await fulfill(route, [])
      return
    }

    if (pathname === '/api/v1/skills/868/versions/8681/security-audit') {
      await fulfill(route, audits)
      return
    }

    if (pathname === '/api/web/skills/868/reviews') {
      await fulfill(route, { items: [], total: 0, page: 0, size: 20 })
      return
    }

    if (pathname === '/api/web/skills/868/reviews/me') {
      await fulfill(route, null)
      return
    }

    if (pathname === '/api/web/notifications/unread-count') {
      await fulfill(route, { count: 0 })
      return
    }

    await fulfill(route, [])
  })
}

test.describe('Security audit redaction', () => {
  test.beforeEach(async ({ page }) => {
    await setEnglishLocale(page)
  })

  test('shows the precise SAFE verdict and only the masked finding snippet', async ({ page }) => {
    await mockSkillDetail(page, [{
      id: 1,
      scanId: 'scan-868',
      scannerType: 'builtin',
      verdict: 'SAFE',
      isSafe: true,
      maxSeverity: 'MEDIUM',
      findingsCount: 1,
      findings: [{
        ruleId: 'SECRET-001',
        severity: 'MEDIUM',
        category: 'secret',
        title: 'Embedded credential',
        message: 'A credential-like value was detected.',
        filePath: 'scripts/deploy.ts',
        lineNumber: 7,
        codeSnippet: MASKED_SNIPPET,
        remediation: 'Read credentials from the environment.',
        analyzer: 'synthetic',
        metadata: { originalSnippet: CANARY },
      }],
      scanDurationSeconds: 1.2,
      failureReason: null,
      scannedAt: '2026-09-17T00:00:00Z',
      createdAt: '2026-09-17T00:00:00Z',
    }])

    await page.goto('/space/global/redaction-skill')

    await expect(page.getByRole('heading', { name: 'Redaction Skill', exact: true }).first()).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('No high-risk findings', { exact: true })).toBeVisible()
    await page.getByRole('button', { name: 'View Details' }).click()
    await page.getByRole('button', { name: 'Findings' }).click()

    await expect(page.getByText(MASKED_SNIPPET, { exact: true })).toBeVisible()
    await expect(page.locator('body')).not.toContainText(CANARY)
  })

  test('keeps the skill detail usable when the audit list is empty', async ({ page }) => {
    await mockSkillDetail(page, [])

    await page.goto('/space/global/redaction-skill')

    await expect(page.getByRole('heading', { name: 'Redaction Skill', exact: true }).first()).toBeVisible({ timeout: 15_000 })
    await expect(page.getByText('Security Audit', { exact: true })).toHaveCount(0)
    await expect(page.locator('body')).not.toContainText(CANARY)
  })
})
