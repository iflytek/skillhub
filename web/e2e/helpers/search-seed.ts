import type { Browser, Locator, Page, TestInfo } from '@playwright/test'
import { createFreshSession, loginWithCredentials, registerSession } from './session'
import { E2eTestDataBuilder, type SeededNamespace, type SeededSkill } from './test-data-builder'

export const DEFAULT_SEARCH_KEYWORD = 'agent'

export interface SearchSeedContext {
  builder: E2eTestDataBuilder
  keyword: string
  namespace: SeededNamespace
  skills: SeededSkill[]
  skillNames: string[]
}

export interface PreparedSearchSeed extends SearchSeedContext {
  dispose: () => Promise<void>
}

interface PublisherSession {
  builder: E2eTestDataBuilder
  context: Awaited<ReturnType<Browser['newContext']>>
  namespace: SeededNamespace
  page: Page
}

function requireEnv(name: string): string {
  const value = process.env[name]
  if (!value) {
    throw new Error(`Missing required E2E env: ${name}`)
  }
  return value
}

function getOptionalEnv(name: string): string | undefined {
  const value = process.env[name]?.trim()
  return value ? value : undefined
}

function publisherCredentials() {
  return {
    username: requireEnv('E2E_PUBLISH_USERNAME'),
    password: requireEnv('E2E_PUBLISH_PASSWORD'),
  }
}

function adminCredentials() {
  return {
    username: getOptionalEnv('E2E_ADMIN_USERNAME') ?? getOptionalEnv('BOOTSTRAP_ADMIN_USERNAME') ?? 'admin',
    password: getOptionalEnv('E2E_ADMIN_PASSWORD') ?? getOptionalEnv('BOOTSTRAP_ADMIN_PASSWORD') ?? 'ChangeMe!2026',
  }
}

function hasPublisherCredentials() {
  return Boolean(getOptionalEnv('E2E_PUBLISH_USERNAME') && getOptionalEnv('E2E_PUBLISH_PASSWORD'))
}

async function openProvidedPublisherSession(browser: Browser, testInfo: TestInfo): Promise<PublisherSession> {
  const context = await browser.newContext()
  const page = await context.newPage()
  const builder = new E2eTestDataBuilder(page, testInfo)

  await loginWithCredentials(page, publisherCredentials(), testInfo)
  await builder.init()

  return {
    builder,
    context,
    namespace: await builder.ensureWritableNamespace(),
    page,
  }
}

async function openAdhocPublisherSession(browser: Browser, testInfo: TestInfo): Promise<PublisherSession> {
  const context = await browser.newContext()
  const page = await context.newPage()
  const builder = new E2eTestDataBuilder(page, testInfo)

  try {
    await createFreshSession(page, testInfo)
  } catch {
    // Fall back to a regular worker session when transient registration issues happen
    // after Playwright restarts the worker following an earlier test failure.
    await registerSession(page, testInfo)
  }
  await builder.init()

  return {
    builder,
    context,
    namespace: await builder.ensureWritableNamespace(),
    page,
  }
}

async function publishSearchSkillsChunk(
  session: PublisherSession,
  keyword: string,
  description: string,
  seedSuffix: string,
  startIndex: number,
  count: number,
) {
  const skills: SeededSkill[] = []
  const skillNames: string[] = []

  for (let offset = 0; offset < count; offset += 1) {
    const skillIndex = startIndex + offset + 1
    const skillName = `${keyword}-search-${skillIndex}-${seedSuffix}`.slice(0, 48)
    const skill = await session.builder.publishSkill(session.namespace.slug, {
      name: skillName,
      description,
    })
    skills.push(skill)
    skillNames.push(skillName)
  }

  return { skillNames, skills }
}

export async function seedPublicSearchSkills(
  page: Page,
  testInfo: TestInfo,
  options?: {
    awaitSearchIndexed?: boolean
    count?: number
    keyword?: string
    description?: string
  },
): Promise<SearchSeedContext> {
  const count = options?.count ?? 1
  const builder = new E2eTestDataBuilder(page, testInfo)
  const seedSuffix = `${testInfo.parallelIndex ?? 0}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`
  const keyword = options?.keyword || `agent-${seedSuffix}`.slice(0, 32)

  await loginWithCredentials(page, publisherCredentials(), testInfo)
  await builder.init()

  const namespace = await builder.ensureWritableNamespace()
  const skills: SeededSkill[] = []
  const skillNames: string[] = []

  for (let index = 0; index < count; index += 1) {
    const skillName = `${keyword}-search-${index + 1}-${seedSuffix}`.slice(0, 48)
    const skill = await builder.publishSkill(namespace.slug, {
      name: skillName,
      description: options?.description || `Searchable ${keyword} skill ${index + 1} for Playwright E2E coverage.`,
    })
    skills.push(skill)
    skillNames.push(skillName)
  }

  if (options?.awaitSearchIndexed ?? true) {
    await builder.waitForSearchResults(keyword, skills.map((skill) => skill.slug))
  }

  return {
    builder,
    keyword,
    namespace,
    skills,
    skillNames,
  }
}

export async function cleanupSearchSeed(seed?: SearchSeedContext) {
  if (seed) {
    await seed.builder.cleanup()
  }
}

export async function prepareSearchSeed(
  browser: Browser,
  testInfo: TestInfo,
  options?: {
    awaitSearchIndexed?: boolean
    count?: number
    keyword?: string
    description?: string
  },
): Promise<PreparedSearchSeed> {
  const count = options?.count ?? 1
  const seedSuffix = `${testInfo.parallelIndex ?? 0}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`
  const keyword = options?.keyword || `agent-${seedSuffix}`.slice(0, 32)
  const description = options?.description || `Searchable ${keyword} skill for Playwright E2E coverage.`
  const useProvidedPublisher = count <= 3 && hasPublisherCredentials()
  const publisherSessions: PublisherSession[] = [
    useProvidedPublisher
      ? await openProvidedPublisherSession(browser, testInfo)
      : await openAdhocPublisherSession(browser, testInfo),
  ]
  const skills: SeededSkill[] = []
  const skillNames: string[] = []
  let publishedCount = 0

  while (publishedCount < count) {
    if (publishedCount >= 10 && publisherSessions.length === 1) {
      publisherSessions.push(await openAdhocPublisherSession(browser, testInfo))
    }

    const activeSession = publishedCount < 10 ? publisherSessions[0] : publisherSessions[publisherSessions.length - 1]
    const chunkSize = publishedCount < 10 ? Math.min(10 - publishedCount, count - publishedCount) : count - publishedCount
    const chunk = await publishSearchSkillsChunk(
      activeSession,
      keyword,
      description,
      seedSuffix,
      publishedCount,
      chunkSize,
    )
    skills.push(...chunk.skills)
    skillNames.push(...chunk.skillNames)
    publishedCount += chunkSize
  }

  const seed: SearchSeedContext = {
    builder: publisherSessions[0].builder,
    keyword,
    namespace: publisherSessions[0].namespace,
    skills,
    skillNames,
  }

  const adminContext = await browser.newContext()
  const adminPage = await adminContext.newPage()
  const adminBuilder = new E2eTestDataBuilder(adminPage, testInfo)

  await loginWithCredentials(adminPage, adminCredentials(), testInfo)
  await adminBuilder.init()

  for (const skill of seed.skills) {
    const reviewTaskId = await adminBuilder.waitForPendingReview(skill.namespace, skill.slug, skill.version)
    await adminBuilder.approveReview(reviewTaskId)
  }

  await seed.builder.waitForSearchResults(seed.keyword, seed.skills.map((skill) => skill.slug))

  return {
    ...seed,
    dispose: async () => {
      await adminContext.close()
      for (let index = publisherSessions.length - 1; index >= 0; index -= 1) {
        await cleanupSearchSeed({
          builder: publisherSessions[index].builder,
          keyword: seed.keyword,
          namespace: publisherSessions[index].namespace,
          skills: [],
          skillNames: [],
        })
        await publisherSessions[index].context.close()
      }
    },
  }
}

export function getSearchCard(page: Page, skillName: string): Locator {
  return getSearchCards(page).filter({
    has: page.getByRole('heading', { name: skillName, exact: true }),
  }).first()
}

export function getSearchCards(page: Page): Locator {
  return page.getByRole('link').filter({
    has: page.locator('h3'),
  })
}
