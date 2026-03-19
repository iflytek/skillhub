import type { components } from './generated/schema'

export type User = Omit<components['schemas']['AuthMeResponse'], 'userId' | 'displayName' | 'platformRoles'> & {
  userId: string
  displayName: string
  email?: string
  avatarUrl?: string
  oauthProvider?: string
  platformRoles: string[]
}

export type OAuthProvider = Omit<components['schemas']['AuthProviderResponse'], 'id' | 'name' | 'authorizationUrl'> & {
  id: string
  name: string
  authorizationUrl: string
}

export interface AuthMethod {
  id: string
  methodType: 'PASSWORD' | 'OAUTH_REDIRECT' | 'DIRECT_PASSWORD' | 'SESSION_BOOTSTRAP' | string
  provider: string
  displayName: string
  actionUrl: string
}

export type ApiToken = Omit<components['schemas']['TokenSummaryResponse'], 'id' | 'name' | 'tokenPrefix' | 'createdAt'> & {
  id: number
  name: string
  tokenPrefix: string
  createdAt: string
  expiresAt?: string
  lastUsedAt?: string
}

export type CreateTokenRequest = Omit<components['schemas']['TokenCreateRequest'], 'name'> & {
  name: string
  scopes?: string[]
  expiresAt?: string
}

export type CreateTokenResponse = Omit<components['schemas']['TokenCreateResponse'], 'token' | 'id' | 'name' | 'tokenPrefix' | 'createdAt'> & {
  token: string
  id: number
  name: string
  tokenPrefix: string
  createdAt: string
  expiresAt?: string
}

export interface LocalLoginRequest {
  username: string
  password: string
}

export interface LocalRegisterRequest extends LocalLoginRequest {
  email?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export type CreateNamespaceRequest = Omit<components['schemas']['NamespaceRequest'], 'slug' | 'displayName'> & {
  slug: string
  displayName: string
  description?: string
}

export interface MergeInitiateRequest {
  secondaryIdentifier: string
}

export interface MergeInitiateResponse {
  mergeRequestId: number
  secondaryUserId: string
  verificationToken: string
  expiresAt: string
}

export interface MergeVerifyRequest {
  mergeRequestId: number
  verificationToken: string
}

export interface MergeConfirmRequest {
  mergeRequestId: number
}

// Namespace types
export type NamespaceStatus = 'ACTIVE' | 'FROZEN' | 'ARCHIVED' | string
export type NamespaceRole = 'OWNER' | 'ADMIN' | 'MEMBER' | string

export interface Namespace {
  id: number
  slug: string
  displayName: string
  description?: string
  type: 'GLOBAL' | 'TEAM'
  avatarUrl?: string
  status: NamespaceStatus
  createdAt: string
  updatedAt?: string
}

export interface ManagedNamespace extends Namespace {
  createdBy?: string
  currentUserRole?: NamespaceRole
  immutable: boolean
  canFreeze: boolean
  canUnfreeze: boolean
  canArchive: boolean
  canRestore: boolean
}

export interface NamespaceMember {
  id: number
  userId: string
  role: NamespaceRole
  createdAt: string
}

export interface NamespaceCandidateUser {
  userId: string
  displayName: string
  email?: string
  status: string
}

// Skill types
export interface SkillSummary {
  id: number
  slug: string
  displayName: string
  summary?: string
  status?: string
  downloadCount: number
  starCount: number
  ratingAvg?: number
  ratingCount: number
  namespace: string
  updatedAt: string
  canSubmitPromotion: boolean
  headlineVersion?: SkillLifecycleVersion
  publishedVersion?: SkillLifecycleVersion
  ownerPreviewVersion?: SkillLifecycleVersion
  resolutionMode?: string
}

export interface SkillLifecycleVersion {
  id: number
  version: string
  status: string
}

export interface SkillDetail {
  id: number
  slug: string
  displayName: string
  ownerDisplayName?: string
  summary?: string
  visibility: string
  status: string
  downloadCount: number
  starCount: number
  ratingAvg?: number
  ratingCount: number
  hidden: boolean
  namespace: string
  canManageLifecycle: boolean
  canSubmitPromotion: boolean
  canInteract: boolean
  canReport: boolean
  headlineVersion?: SkillLifecycleVersion
  publishedVersion?: SkillLifecycleVersion
  ownerPreviewVersion?: SkillLifecycleVersion
  resolutionMode?: string
}

export interface SubmitPromotionRequest {
  sourceSkillId: number
  sourceVersionId: number
  targetNamespaceId: number
}

export interface SkillVersion {
  id: number
  version: string
  status: string
  changelog?: string
  fileCount: number
  totalSize: number
  publishedAt: string
  downloadAvailable: boolean
}

export interface SkillVersionDetail {
  id: number
  version: string
  status: string
  changelog?: string
  fileCount: number
  totalSize: number
  publishedAt: string
  parsedMetadataJson?: string
  manifestJson?: string
}

export interface SkillFile {
  id: number
  filePath: string
  fileSize: number
  contentType: string
  sha256: string
}

export interface SkillTag {
  id: number
  tagName: string
  versionId: number
  createdAt: string
}

// Search and pagination
export interface SearchParams {
  q?: string
  namespace?: string
  sort?: string
  page?: number
  size?: number
  starredOnly?: boolean
}

export interface PagedResponse<T> {
  items: T[]
  total: number
  page: number
  size: number
}

// Publish
export interface PublishResult {
  skillId: number
  namespace: string
  slug: string
  version: string
  status: string
  fileCount: number
  totalSize: number
}

export interface ReviewTask {
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

export interface PromotionTask {
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

export interface SkillReport {
  id: number
  skillId: number
  namespace?: string
  skillSlug?: string
  skillDisplayName?: string
  reporterId: string
  reason: string
  details?: string
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED' | string
  handledBy?: string
  handleComment?: string
  createdAt: string
  handledAt?: string
}

export type ReportDisposition = 'RESOLVE_ONLY' | 'RESOLVE_AND_HIDE' | 'RESOLVE_AND_ARCHIVE'

export interface GovernanceSummary {
  pendingReviews: number
  pendingPromotions: number
  pendingReports: number
  unreadNotifications: number
}

export interface GovernanceInboxItem {
  type: 'REVIEW' | 'PROMOTION' | 'REPORT' | string
  id: number
  title: string
  subtitle?: string
  timestamp?: string
  namespace?: string
  skillSlug?: string
}

export interface GovernanceActivityItem {
  id: number
  action: string
  actorUserId?: string
  actorDisplayName?: string
  targetType?: string
  targetId?: string
  details?: string
  timestamp?: string
}

export interface GovernanceNotification {
  id?: number
  category: string
  entityType: string
  entityId: number
  title: string
  bodyJson?: string
  status: 'UNREAD' | 'READ' | string
  createdAt?: string
  readAt?: string
}

export interface AdminUser {
  userId: string
  username: string
  email?: string
  platformRoles: string[]
  status: string
  createdAt: string
}

export interface AuditLogItem {
  id: string
  userId?: string
  username?: string
  action: string
  details?: string
  requestId?: string
  resourceType?: string
  resourceId?: string
  timestamp: string
  ipAddress?: string
}
