import type { Route } from '@playwright/test'

type EnvelopeOptions = {
  status?: number
  msg?: string
}

type PagedOptions = {
  page?: number
  size?: number
  total?: number
}

type SearchResponse<T> = {
  items: T[]
  total: number
  page: number
  size: number
}

export type TestUser = {
  userId: string
  displayName: string
  email: string
  avatarUrl?: string
  oauthProvider?: string
  platformRoles: string[]
  status: string
}

export type TestNamespace = {
  id?: number
  slug: string
  displayName: string
  description?: string
  avatarUrl?: string
  type: 'GLOBAL' | 'TEAM'
  status: 'ACTIVE' | 'FROZEN' | 'ARCHIVED'
  createdAt?: string
  updatedAt?: string
}

export type TestManagedNamespace = TestNamespace & {
  immutable: boolean
  canFreeze: boolean
  canUnfreeze: boolean
  canArchive: boolean
  canRestore: boolean
  currentUserRole?: string
  createdBy?: string
}

export type TestNamespaceMember = {
  id: number
  userId: string
  role: string
  createdAt: string
}

export type TestNamespaceCandidateUser = {
  userId: string
  displayName: string
  email?: string
  status: string
}

export type TestSkillSummary = {
  id: number
  slug: string
  displayName: string
  summary?: string
  status?: string
  downloadCount: number
  starCount: number
  ratingCount: number
  ratingAvg?: number
  namespace: string
  updatedAt: string
  canSubmitPromotion: boolean
  headlineVersion?: {
    id: number
    version: string
    status: string
  }
  publishedVersion?: {
    id: number
    version: string
    status: string
  }
  ownerPreviewVersion?: {
    id: number
    version: string
    status: string
  }
  resolutionMode?: string
}

export type TestSkillDetail = TestSkillSummary & {
  ownerId: string
  ownerDisplayName?: string
  canInteract: boolean
  canReport: boolean
  canManageLifecycle: boolean
  status: 'ACTIVE' | 'ARCHIVED'
  labels?: Array<{
    slug: string
    displayName: string
    type: string
  }>
  resolutionMode?: 'PUBLIC' | 'OWNER_PREVIEW'
  ownerPreviewReviewComment?: string
}

export type TestSkillVersion = {
  id: number
  version: string
  status: string
  createdAt: string
  publishedAt: string
  changelog?: string
  fileCount: number
  totalSize: number
  downloadAvailable?: boolean
}

export type TestSkillFile = {
  id: number
  filePath: string
  fileSize: number
  contentType?: string
}

export type TestReview = {
  id: number
  skillId: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedAt: string
  reviewedAt?: string
  comment?: string
}

export type TestReviewTask = {
  id: number
  skillVersionId: number
  namespace: string
  skillSlug: string
  version: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedBy: string
  submittedByName?: string
  reviewedBy?: string
  reviewedByName?: string
  reviewComment?: string
  submittedAt: string
  reviewedAt?: string
}

export type TestReviewSkill = {
  id: number
  slug: string
  displayName: string
  visibility: string
  status: string
  downloadCount: number
  starCount: number
  ratingCount: number
  hidden: boolean
  namespace: string
  canManageLifecycle: boolean
  canSubmitPromotion: boolean
  canInteract: boolean
  canReport: boolean
  resolutionMode: string
}

export type TestReviewSkillDetail = {
  skill: TestReviewSkill
  versions: TestSkillVersion[]
  files: TestSkillFile[]
  documentationPath?: string
  documentationContent?: string
  downloadUrl: string
  activeVersion: string
}

export type TestPromotionTask = {
  id: number
  sourceSkillId: number
  sourceNamespace: string
  sourceSkillSlug: string
  sourceVersion: string
  targetNamespace: string
  targetSkillId?: number
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedBy: string
  submittedByName?: string
  reviewedBy?: string
  reviewedByName?: string
  reviewComment?: string
  submittedAt: string
  reviewedAt?: string
}

export type TestSkillReport = {
  id: number
  skillId: number
  namespace?: string
  skillSlug?: string
  skillDisplayName?: string
  reporterId: string
  reason: string
  details?: string
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED'
  handledBy?: string
  handleComment?: string
  createdAt: string
  handledAt?: string
}

export type TestAdminUser = {
  userId: string
  username: string
  email?: string
  platformRoles: string[]
  status: 'ACTIVE' | 'PENDING' | 'DISABLED'
  createdAt: string
}

export type TestAuditLogItem = {
  id: number
  action: string
  userId?: string
  username?: string
  ipAddress?: string
  details?: string
  resourceType?: string
  resourceId?: string
  requestId?: string
  timestamp: string
}

export type TestLabelDefinition = {
  slug: string
  type: 'RECOMMENDED' | 'PRIVILEGED'
  visibleInFilter: boolean
  sortOrder: number
  translations: Array<{
    locale: string
    displayName: string
  }>
  createdAt?: string
}

export type TestToken = {
  id: number
  name: string
  token?: string
  createdAt: string
  expiresAt?: string
}

export type TestNotification = {
  id: number
  type: string
  title: string
  content: string
  read: boolean
  createdAt: string
}

export type TestAuthMethod = {
  id: string
  methodType: string
  provider: string
  displayName: string
  actionUrl: string
}

export const JSON_HEADERS = {
  'access-control-allow-origin': '*',
  'content-type': 'application/json',
}

export function envelope<T>(data: T, options: EnvelopeOptions = {}) {
  return JSON.stringify({
    code: options.status && options.status >= 400 ? options.status : 0,
    msg: options.msg ?? 'ok',
    data,
    timestamp: '2026-03-31T00:00:00Z',
    requestId: 'playwright-e2e',
  })
}

export async function fulfillJson<T>(route: Route, data: T, options: EnvelopeOptions = {}) {
  await route.fulfill({
    status: options.status ?? 200,
    headers: JSON_HEADERS,
    body: envelope(data, options),
  })
}

export async function fulfillText(route: Route, body: string, contentType: string = 'text/plain; charset=utf-8') {
  await route.fulfill({
    status: 200,
    headers: {
      'access-control-allow-origin': '*',
      'content-type': contentType,
    },
    body,
  })
}

export function paged<T>(items: T[], options: PagedOptions = {}): SearchResponse<T> {
  return {
    items,
    total: options.total ?? items.length,
    page: options.page ?? 0,
    size: options.size ?? Math.max(items.length, 1),
  }
}

export function user(
  userId: string,
  displayName: string,
  role: string = 'USER',
  overrides: Partial<TestUser> = {},
): TestUser {
  return {
    userId,
    displayName,
    email: `${userId}@example.com`,
    oauthProvider: 'local',
    platformRoles: [role],
    status: 'ACTIVE',
    ...overrides,
  }
}

export function namespace(
  slug: string,
  displayName: string,
  overrides: Partial<TestNamespace> = {},
): TestNamespace {
  return {
    id: 100,
    slug,
    displayName,
    description: `${displayName} namespace`,
    type: 'TEAM',
    status: 'ACTIVE',
    createdAt: '2026-03-20T00:00:00Z',
    updatedAt: '2026-03-20T00:00:00Z',
    ...overrides,
  }
}

export function managedNamespace(
  slug: string,
  displayName: string,
  overrides: Partial<TestManagedNamespace> = {},
): TestManagedNamespace {
  return {
    ...namespace(slug, displayName),
    immutable: false,
    canFreeze: true,
    canUnfreeze: true,
    canArchive: true,
    canRestore: true,
    currentUserRole: 'OWNER',
    createdBy: 'local-user',
    ...overrides,
  }
}

export function namespaceMember(
  id: number,
  userId: string,
  role: string,
  overrides: Partial<TestNamespaceMember> = {},
): TestNamespaceMember {
  return {
    id,
    userId,
    role,
    createdAt: '2026-03-20T00:00:00Z',
    ...overrides,
  }
}

export function namespaceCandidateUser(
  userId: string,
  displayName: string,
  overrides: Partial<TestNamespaceCandidateUser> = {},
): TestNamespaceCandidateUser {
  return {
    userId,
    displayName,
    email: `${userId}@example.com`,
    status: 'ACTIVE',
    ...overrides,
  }
}

export function skill(
  id: number,
  displayName: string,
  overrides: Partial<TestSkillSummary> = {},
): TestSkillSummary {
  return {
    id,
    slug: displayName.toLowerCase().replace(/\s+/g, '-'),
    displayName,
    summary: `${displayName} summary`,
    downloadCount: 100 + id,
    starCount: 10 + id,
    ratingCount: 0,
    namespace: 'global',
    updatedAt: '2026-03-20T00:00:00Z',
    canSubmitPromotion: false,
    headlineVersion: {
      id: id * 10,
      version: '1.0.0',
      status: 'PUBLISHED',
    },
    publishedVersion: {
      id: id * 10,
      version: '1.0.0',
      status: 'PUBLISHED',
    },
    ...overrides,
  }
}

export function skillDetail(
  id: number,
  displayName: string,
  overrides: Partial<TestSkillDetail> = {},
): TestSkillDetail {
  const baseSkill = skill(id, displayName, overrides)
  return {
    ...baseSkill,
    ownerId: 'local-user',
    ownerDisplayName: 'Local User',
    canInteract: true,
    canReport: true,
    canManageLifecycle: false,
    status: 'ACTIVE',
    labels: [],
    resolutionMode: 'PUBLIC',
    ...overrides,
  }
}

export function skillVersion(
  id: number,
  version: string,
  overrides: Partial<TestSkillVersion> = {},
): TestSkillVersion {
  return {
    id,
    version,
    status: 'PUBLISHED',
    createdAt: '2026-03-20T00:00:00Z',
    publishedAt: '2026-03-20T00:00:00Z',
    changelog: `Release ${version}`,
    fileCount: 2,
    totalSize: 4096,
    downloadAvailable: true,
    ...overrides,
  }
}

export function reviewTask(
  id: number,
  status: 'PENDING' | 'APPROVED' | 'REJECTED',
  overrides: Partial<TestReviewTask> = {},
): TestReviewTask {
  return {
    id,
    skillVersionId: id * 10,
    namespace: 'team-alpha',
    skillSlug: `skill-${id}`,
    version: `1.${id}.0`,
    status,
    submittedBy: 'local-user',
    submittedByName: 'Local User',
    reviewComment: status === 'REJECTED' ? 'Needs more detail' : undefined,
    submittedAt: '2026-03-20T00:00:00Z',
    reviewedAt: status === 'PENDING' ? undefined : '2026-03-21T00:00:00Z',
    ...overrides,
  }
}

export function promotionTask(
  id: number,
  status: 'PENDING' | 'APPROVED' | 'REJECTED',
  overrides: Partial<TestPromotionTask> = {},
): TestPromotionTask {
  return {
    id,
    sourceSkillId: id * 10,
    sourceNamespace: 'team-alpha',
    sourceSkillSlug: `skill-${id}`,
    sourceVersion: `1.${id}.0`,
    targetNamespace: 'global',
    status,
    submittedBy: 'local-user',
    submittedByName: 'Local User',
    reviewComment: status === 'REJECTED' ? 'Does not meet quality bar' : undefined,
    submittedAt: '2026-03-20T00:00:00Z',
    reviewedAt: status === 'PENDING' ? undefined : '2026-03-21T00:00:00Z',
    reviewedByName: status === 'PENDING' ? undefined : 'Skill Reviewer',
    ...overrides,
  }
}

export function reviewSkillDetail(
  id: number,
  displayName: string,
  overrides: Partial<TestReviewSkillDetail> = {},
): TestReviewSkillDetail {
  const baseVersion = skillVersion(id * 10, '1.0.0', { status: 'PENDING_REVIEW' })
  return {
    skill: {
      id,
      slug: displayName.toLowerCase().replace(/\s+/g, '-'),
      displayName,
      visibility: 'PUBLIC',
      status: 'ACTIVE',
      downloadCount: 0,
      starCount: 0,
      ratingCount: 0,
      hidden: false,
      namespace: 'team-alpha',
      canManageLifecycle: false,
      canSubmitPromotion: false,
      canInteract: false,
      canReport: false,
      resolutionMode: 'REVIEW_TASK',
    },
    versions: [baseVersion],
    files: [
      skillFile('README.md', { fileSize: 256 }),
      skillFile('guide.txt', { fileSize: 128 }),
    ],
    documentationPath: 'README.md',
    documentationContent: `# ${displayName}\n\nPending review package overview.`,
    downloadUrl: `/api/web/reviews/${id}/download`,
    activeVersion: baseVersion.version,
    ...overrides,
  }
}

export function skillReport(
  id: number,
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED',
  overrides: Partial<TestSkillReport> = {},
): TestSkillReport {
  return {
    id,
    skillId: id * 10,
    namespace: 'team-alpha',
    skillSlug: `skill-${id}`,
    skillDisplayName: `Skill ${id}`,
    reporterId: 'reporter-user',
    reason: 'Potential policy violation',
    details: 'Contains unsafe instructions',
    status,
    handledBy: status === 'PENDING' ? undefined : 'skill-admin',
    handleComment: undefined,
    createdAt: '2026-03-20T00:00:00Z',
    handledAt: status === 'PENDING' ? undefined : '2026-03-21T00:00:00Z',
    ...overrides,
  }
}

export function adminUser(
  userId: string,
  username: string,
  status: 'ACTIVE' | 'PENDING' | 'DISABLED' = 'ACTIVE',
  overrides: Partial<TestAdminUser> = {},
): TestAdminUser {
  return {
    userId,
    username,
    email: `${userId}@example.com`,
    platformRoles: ['USER'],
    status,
    createdAt: '2026-03-20T00:00:00Z',
    ...overrides,
  }
}

export function auditLogItem(
  id: number,
  action: string,
  overrides: Partial<TestAuditLogItem> = {},
): TestAuditLogItem {
  return {
    id,
    action,
    userId: 'local-user',
    username: 'Local User',
    ipAddress: '127.0.0.1',
    details: `${action} completed`,
    resourceType: 'SKILL',
    resourceId: String(id),
    requestId: `req-${id}`,
    timestamp: '2026-03-20T00:00:00Z',
    ...overrides,
  }
}

export function labelDefinition(
  slug: string,
  displayName: string,
  overrides: Partial<TestLabelDefinition> = {},
): TestLabelDefinition {
  return {
    slug,
    type: 'RECOMMENDED',
    visibleInFilter: true,
    sortOrder: 0,
    translations: [
      {
        locale: 'en',
        displayName,
      },
    ],
    createdAt: '2026-03-20T00:00:00Z',
    ...overrides,
  }
}

export function skillFile(
  filePath: string,
  overrides: Partial<TestSkillFile> = {},
): TestSkillFile {
  return {
    id: Math.abs([...filePath].reduce((sum, char) => sum + char.charCodeAt(0), 0)),
    filePath,
    fileSize: 512,
    contentType: filePath.endsWith('.md') ? 'text/markdown' : 'text/plain',
    ...overrides,
  }
}

export function review(
  id: number,
  skillId: number,
  status: TestReview['status'] = 'PENDING',
  overrides: Partial<TestReview> = {},
): TestReview {
  return {
    id,
    skillId,
    status,
    submittedAt: '2026-03-20T00:00:00Z',
    ...overrides,
  }
}

export function token(
  id: number,
  name: string,
  overrides: Partial<TestToken> = {},
): TestToken {
  return {
    id,
    name,
    createdAt: '2026-03-20T00:00:00Z',
    expiresAt: '2027-03-20T00:00:00Z',
    ...overrides,
  }
}

export function notification(
  id: number,
  type: string,
  read: boolean = false,
  overrides: Partial<TestNotification> = {},
): TestNotification {
  return {
    id,
    type,
    title: `Notification ${id}`,
    content: `Content for notification ${id}`,
    read,
    createdAt: '2026-03-20T00:00:00Z',
    ...overrides,
  }
}

export function authMethod(
  id: string,
  displayName: string,
  overrides: Partial<TestAuthMethod> = {},
): TestAuthMethod {
  return {
    id,
    methodType: 'OAUTH_REDIRECT',
    provider: id,
    displayName,
    actionUrl: `https://auth.example.com/${id}`,
    ...overrides,
  }
}
