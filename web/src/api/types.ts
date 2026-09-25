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
  email: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface PasswordResetRequest {
  email: string
}

export interface PasswordResetConfirmRequest {
  email: string
  code: string
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
  canDelete: boolean
}

export interface AdminNamespacePermissions {
  currentUserRole?: NamespaceRole
  platformOverride: boolean
  immutable: boolean
  canManageMembers: boolean
  canGovernNamespace: boolean
  canPublish: boolean
  canTransferOwnership: boolean
  canFreeze: boolean
  canUnfreeze: boolean
  canArchive: boolean
  canRestore: boolean
}

export interface AdminNamespaceStats {
  memberCount: number
  skillCount: number
}

export interface AdminNamespace extends Namespace {
  createdBy?: string
  stats: AdminNamespaceStats
  permissions: AdminNamespacePermissions
}

export interface AdminNamespaceListStats {
  total: number
  active: number
  frozen: number
  archived: number
}

export interface AdminNamespaceList {
  items: AdminNamespace[]
  total: number
  page: number
  size: number
  stats: AdminNamespaceListStats
}

export interface NamespaceMember {
  id: number
  userId: string
  displayName?: string
  email?: string
  role: NamespaceRole
  createdAt: string
}

export interface NamespaceCandidateUser {
  userId: string
  displayName: string
  email?: string
  status: string
}

export interface BatchMemberResult {
  userId: string
  role: string
  success: boolean
  error?: string
}

export interface BatchMemberResponse {
  totalCount: number
  successCount: number
  failureCount: number
  results: BatchMemberResult[]
}

// Skill types
export interface SkillSummary {
  id: number
  slug: string
  displayName: string
  summary?: string
  visibility?: string
  status?: string
  downloadCount: number
  starCount: number
  ratingAvg?: number
  ratingCount: number
  namespace: string
  updatedAt: string
  ownerId?: string
  ownerDisplayName?: string
  canSubmitPromotion: boolean
  headlineVersion?: SkillLifecycleVersion
  publishedVersion?: SkillLifecycleVersion
  ownerPreviewVersion?: SkillLifecycleVersion
  resolutionMode?: string
  complianceSnapshot?: ComplianceSnapshot
}

export type LabelItem = Omit<components['schemas']['SkillLabelDto'], 'slug' | 'type' | 'displayName'> & {
  slug: string
  type: 'RECOMMENDED' | 'PRIVILEGED' | string
  displayName: string
}

export type LabelTranslation = Omit<components['schemas']['LabelTranslationResponse'], 'locale' | 'displayName'> & {
  locale: string
  displayName: string
}

export type LabelDefinition = Omit<
  components['schemas']['LabelDefinitionResponse'],
  'slug' | 'type' | 'translations' | 'sortOrder' | 'visibleInFilter'
> & {
  slug: string
  type: 'RECOMMENDED' | 'PRIVILEGED' | string
  visibleInFilter: boolean
  sortOrder: number
  translations: LabelTranslation[]
}

export interface AdminLabelInput {
  slug: string
  type: 'RECOMMENDED' | 'PRIVILEGED'
  visibleInFilter: boolean
  sortOrder: number
  translations: LabelTranslation[]
}

export interface SkillLifecycleVersion {
  id: number
  version: string
  status: string
}

export interface ComplianceEvidence {
  type?: string
  path?: string
  url?: string
  sha256?: string
}

export interface ComplianceMapping {
  standard?: string
  version?: string
  controlId?: string
  title?: string
  evidence?: ComplianceEvidence[]
}

export interface ComplianceSnapshot {
  schemaVersion?: string
  items?: ComplianceMapping[]
  digest?: string
}

type GeneratedSkillSuiteSiblingMember = components['schemas']['SkillSuiteSiblingMemberResponse']
export type SkillSuiteSiblingMember = Required<GeneratedSkillSuiteSiblingMember>

type GeneratedSkillSuiteReference = components['schemas']['SkillSuiteReferenceResponse']
export type SkillSuiteReference = Omit<
  RequiredGenerated<
    GeneratedSkillSuiteReference,
    'currentSkillEntry' | 'visibleSiblingMembers' | 'restrictedMemberCount' | 'omittedVisibleMemberCount'
  >,
  'visibleSiblingMembers'
> & {
  visibleSiblingMembers?: SkillSuiteSiblingMember[]
}

type GeneratedSkillDetail = components['schemas']['SkillDetailResponse']
export type SkillDetail = Omit<
  RequiredGenerated<
    GeneratedSkillDetail,
    | 'ownerId'
    | 'ownerDisplayName'
    | 'summary'
    | 'subscriptionCount'
    | 'ratingAvg'
    | 'labels'
    | 'headlineVersion'
    | 'publishedVersion'
    | 'ownerPreviewVersion'
    | 'ownerPreviewReviewComment'
    | 'resolutionMode'
    | 'entryForSuites'
    | 'memberOfSuites'
  >,
  'labels' | 'headlineVersion' | 'publishedVersion' | 'ownerPreviewVersion' | 'entryForSuites' | 'memberOfSuites'
> & {
  labels?: LabelItem[]
  headlineVersion?: SkillLifecycleVersion
  publishedVersion?: SkillLifecycleVersion
  ownerPreviewVersion?: SkillLifecycleVersion
  entryForSuites?: SkillSuiteReference[]
  memberOfSuites?: PagedResponse<SkillSuiteReference>
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
  complianceSnapshot?: ComplianceSnapshot
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
  complianceSnapshot?: ComplianceSnapshot
}

export interface SkillFile {
  id: number
  filePath: string
  fileSize: number
  contentType: string
  sha256: string
}

export interface SkillVersionCompareLine {
  type: 'CONTEXT' | 'ADD' | 'DELETE' | string
  content: string
  oldLineNumber: number | null
  newLineNumber: number | null
}

export interface SkillVersionCompareHunk {
  oldStart: number
  oldLines: number
  newStart: number
  newLines: number
  lines: SkillVersionCompareLine[]
}

export interface SkillVersionCompareFile {
  path: string
  changeType: 'ADDED' | 'MODIFIED' | 'REMOVED' | string
  oldSize: number | null
  newSize: number | null
  binary: boolean
  truncated: boolean
  hunks: SkillVersionCompareHunk[]
}

export interface SkillVersionCompareSummary {
  totalFiles: number
  addedFiles: number
  modifiedFiles: number
  removedFiles: number
  addedLines: number
  removedLines: number
}

export interface SkillVersionCompare {
  from: string
  to: string
  summary: SkillVersionCompareSummary
  files: SkillVersionCompareFile[]
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
  label?: string
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

export type ResourceType = 'SKILL' | 'SUITE'

type RequiredGenerated<T, Optional extends keyof T = never> =
  Required<Omit<T, Optional>> & Pick<T, Optional>

type GeneratedResourceSummary = components['schemas']['ResourceSummaryResponse']
export type ResourceSummary = Omit<RequiredGenerated<GeneratedResourceSummary, 'summary' | 'labels'>, 'resourceType'> & {
  resourceType: ResourceType
}

export interface ResourceSearchParams {
  q?: string
  namespace?: string
  resourceType?: ResourceType
  labels?: string[]
  sort?: string
  page?: number
  size?: number
}

type GeneratedSuiteMember = components['schemas']['SkillSuiteMemberResponse']
export type SkillSuiteMember = RequiredGenerated<
  GeneratedSuiteMember,
  'skillId' | 'skillVersionId' | 'displayName' | 'summary' | 'blockingReason'
>

type GeneratedSuite = components['schemas']['SkillSuiteResponse']
export type SkillSuite = Omit<
  RequiredGenerated<
    GeneratedSuite,
    'summary' | 'overview' | 'changelog' | 'createdByName' | 'publishedAt' | 'yankedAt'
  >,
  'members'
> & {
  members: SkillSuiteMember[]
}

type GeneratedSuiteVersion = components['schemas']['SkillSuiteVersionSummaryResponse']
export type SkillSuiteVersion = RequiredGenerated<
  GeneratedSuiteVersion,
  'changelog' | 'createdByName' | 'publishedAt' | 'yankedAt'
>

export type SkillSuiteMemberCandidate = RequiredGenerated<
  components['schemas']['SkillSuiteMemberCandidateResponse']
>

export type SkillSuiteMemberInput = components['schemas']['SkillSuiteMemberRequest']
export type SkillSuiteDraftInput = components['schemas']['SkillSuiteCreateRequest']

export type MySkillSuiteSummary = RequiredGenerated<
  components['schemas']['MySkillSuiteSummaryResponse'],
  'summary'
>

export type MySkillSuiteWorkspaceItem = RequiredGenerated<
  components['schemas']['Item'],
  'suiteId' | 'summary' | 'suiteVersion' | 'operationId' | 'operationStatus' | 'failureCode'
>
export type MySkillSuiteWorkspace = Omit<
  RequiredGenerated<components['schemas']['MySkillSuiteWorkspaceResponse']>, 'items'
> & { items: MySkillSuiteWorkspaceItem[] }

export type SkillSuiteBundlePreview = components['schemas']['SkillSuiteBundlePreviewResponse']
export type SkillSuiteBundlePreviewMember = components['schemas']['PreviewMember']
export type SkillSuiteBundleRemovedMember = components['schemas']['RemovedMember']
export type SkillSuiteBundleOperation = components['schemas']['SkillSuiteBundleOperationDetailResponse']
export type SkillSuiteBundleOperationResult = components['schemas']['SkillSuiteBundleOperationResponse']
export type SkillSuiteBundleOperationSummary = RequiredGenerated<
  components['schemas']['SkillSuiteBundleOperationSummaryResponse'],
  'failureCode' | 'baseVersion'
>
type GeneratedSkillSuiteBundleOperationPage = components['schemas']['SkillSuiteBundleOperationPageResponse']
export type SkillSuiteBundleOperationPage = Omit<
  RequiredGenerated<GeneratedSkillSuiteBundleOperationPage>,
  'items'
> & { items: SkillSuiteBundleOperationSummary[] }

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

export interface SkillDeleteResult {
  skillId?: number
  namespace?: string
  slug?: string
  deleted?: boolean
}

export interface ReviewTask {
  id: number
  skillVersionId: number | null
  namespace: string
  skillSlug?: string | null
  version: string
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedBy: string
  submittedByName?: string
  reviewedBy?: string
  reviewedByName?: string
  reviewComment?: string
  submittedAt: string
  reviewedAt?: string
  subjectType?: 'SKILL_VERSION' | 'SUITE_VERSION'
  subjectId?: number | null
  subjectVersionId?: number | null
  subjectSlug?: string | null
}

export interface ReviewProgress extends Pick<components['schemas']['ReviewProgressResponse'],
  'subjectType' | 'subjectId' | 'subjectVersionId' | 'subjectSlug'> {
  latestReviewTaskId: number
  skillId?: number
  namespace: string
  skillSlug?: string
  skillVersion: string
  latestStatus: 'PENDING' | 'APPROVED' | 'REJECTED'
  latestReviewComment?: string
  latestSubmittedAt: string
  latestReviewedAt?: string
  attemptCount: number
}

export interface ReviewProgressStatusCounts {
  pending: number
  approved: number
  rejected: number
}

export interface ReviewProgressPage {
  items: ReviewProgress[]
  total: number
  page: number
  size: number
  statusCounts: ReviewProgressStatusCounts
}

export interface ReviewSkillDetail {
  skill: SkillDetail
  versions: SkillVersion[]
  files: SkillFile[]
  documentationPath?: string
  documentationContent?: string
  downloadUrl: string
  activeVersion: string
}

export type PromotionStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type PromotionSortDirection = 'ASC' | 'DESC'
export type PromotionSortBy = 'reviewedAt'

export interface PromotionTask {
  id: number
  sourceSkillId: number
  sourceSkillDisplayName: string
  sourceSkillSummary?: string | null
  sourceNamespace: string
  sourceSkillSlug: string
  sourceVersion: string
  sourceVersionFileCount: number
  sourceVersionTotalSize: number
  sourceSkillDownloadCount: number
  sourceSkillStarCount: number
  targetNamespace: string
  targetSkillId?: number | null
  status: PromotionStatus
  submittedBy: string
  submittedByName?: string | null
  reviewedBy?: string | null
  reviewedByName?: string | null
  reviewComment?: string | null
  submittedAt: string
  reviewedAt?: string | null
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

// Notification types
export interface NotificationItem {
  id: number
  category: 'PUBLISH' | 'REVIEW' | 'PROMOTION' | 'REPORT'
  eventType: string
  title: string
  bodyJson?: string
  entityType?: string
  entityId?: number
  targetType?: string
  targetId?: number
  targetRoute?: string
  status: 'UNREAD' | 'READ'
  createdAt: string
  readAt?: string
}

export interface NotificationPreferenceItem {
  category: string
  channel: string
  enabled: boolean
}

export interface NotificationUnreadCount {
  count: number
}

// Authoring workbench types (draft create/edit/validate/submit flow)
export interface AuthoringDraft {
  id: number
  namespaceId: number
  name: string
  requirement?: string
  revision: number
  contentDigest: string
  validated: boolean
  validatedRevision?: number
  validatedRunId?: number
  submittedSkillId?: number
  submittedVersionId?: number
  submittedAt?: string
  createdAt: string
  updatedAt: string
}

export interface DraftFileSummary {
  id?: number
  path: string
  sha256: string
  size: number
  contentType?: string
  updatedAt?: string
}

export interface DraftFileContent {
  path: string
  sha256: string
  size: number
  contentType?: string
  content: string
}

export interface SaveDraftFileOutcome {
  draft: AuthoringDraft
  file: DraftFileSummary
  created: boolean
  revisionAdvanced: boolean
}

export interface RuntimeBindingInfo {
  // null while the draft has no saved binding yet (the form falls back to
  // its local-script default)
  agentType: string | null
  config: Record<string, unknown>
  toolAllowlist: string[]
  mcpServers: Record<string, unknown>[]
  updatedAt?: string
}

export type ValidationRunStatus =
  | 'QUEUED'
  | 'PREPARING'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'TIMED_OUT'

export interface ValidationRunInfo {
  id: number
  draftId: number
  draftRevision: number
  status: ValidationRunStatus
  cancelRequested: boolean
  active: boolean
  terminal: boolean
  errorCount: number
  warningCount: number
  triggeredBy: string
  startedAt?: string
  finishedAt?: string
  createdAt: string
  summary: Record<string, unknown>
}

export interface ValidationEventInfo {
  seq: number
  type: string
  phase?: string
  payload: Record<string, unknown>
  createdAt?: string
}

export interface FilePatch {
  filePath: string
  oldSha256?: string
  oldValue?: string
  newValue?: string
}

export interface FixSuggestionInfo {
  description?: string
  patches?: FilePatch[]
}

export interface ValidationFindingInfo {
  id: number
  runId: number
  layer: 'STRUCTURE' | 'CONFIG' | 'BEHAVIOR'
  ruleCode: string
  severity: 'ERROR' | 'WARNING'
  filePath?: string
  location?: string
  message: string
  suggestion?: FixSuggestionInfo | null
  status: 'OPEN' | 'APPLIED' | 'DISMISSED'
  appliedRevision?: number
  createdAt: string
}

export interface SubmitDraftOutcome {
  skillId: number
  versionId: number
  slug: string
  version: string
}
