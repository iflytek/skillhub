import type { Browser, Page, TestInfo } from '@playwright/test'
import { loginWithCredentials, registerSession } from './session'
import { E2eTestDataBuilder, type SeededReviewData } from './test-data-builder'

function getOptionalEnv(name: string): string | undefined {
  const value = process.env[name]?.trim()
  return value ? value : undefined
}

function adminCredentials() {
  return {
    username: getOptionalEnv('E2E_ADMIN_USERNAME') ?? getOptionalEnv('BOOTSTRAP_ADMIN_USERNAME') ?? 'admin',
    password: getOptionalEnv('E2E_ADMIN_PASSWORD') ?? getOptionalEnv('BOOTSTRAP_ADMIN_PASSWORD') ?? 'ChangeMe!2026',
  }
}

function matchCandidateUsername(
  candidate: { userId: string; displayName: string; email?: string },
  username: string,
) {
  return candidate.userId === username
    || candidate.displayName === username
    || candidate.email === `${username}@example.test`
}

export async function createNamespaceReviewData(
  browser: Browser,
  page: Page,
  testInfo: TestInfo,
): Promise<SeededReviewData & { reviewTaskId: number; cleanup: () => Promise<void> }> {
  const credentials = await registerSession(page, testInfo, { allowMockSession: false })
  const builder = new E2eTestDataBuilder(page, testInfo)
  await builder.init()

  const adminContext = await browser.newContext()
  const adminPage = await adminContext.newPage()
  const adminBuilder = new E2eTestDataBuilder(adminPage, testInfo)

  await loginWithCredentials(adminPage, adminCredentials(), testInfo)
  await adminBuilder.init()

  const namespace = await adminBuilder.createNamespace('e2e-team')
  const candidates = await adminBuilder.searchNamespaceMemberCandidates(namespace.slug, credentials.username)
  const matchedCandidate = candidates.find((candidate) => matchCandidateUsername(candidate, credentials.username)) ?? candidates[0]

  if (!matchedCandidate) {
    throw new Error(`No namespace member candidate found for review actor ${credentials.username}`)
  }

  await adminBuilder.addNamespaceMember(namespace.slug, matchedCandidate.userId, 'ADMIN')
  const skill = await builder.publishSkill(namespace.slug)
  const reviewTaskId = await adminBuilder.waitForPendingReview(namespace.slug, skill.slug, skill.version)

  return {
    namespace,
    skill,
    reviewTaskId,
    cleanup: async () => {
      await builder.cleanup()
      await adminBuilder.cleanup()
      await adminContext.close()
    },
  }
}
