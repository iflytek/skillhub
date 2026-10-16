export type TabMode = 'auto' | 'show' | 'hide';
export interface HostExecutePlanRequest { planId: string; requestKey: string }
export interface HostRecoveryRequest { operationId: string; hostInstanceId: string }
export interface HostCompatibility {
  dependencyBaseline: 'matched' | 'unverified';
  candidateDshVersion: string;
  localWritesAllowed: boolean;
}
export interface UpgradePlanView {
  hostInstanceId: string;
  status: 'ready' | 'unchanged' | 'blocked';
  reason: string;
  planId: string | null;
  expiresAt: string | null;
  currentVersion: string;
  candidateVersion: string | null;
  targets: { scope: 'user' | 'project'; path: string }[];
}
export type { NativeOperation, NativeInstallAcceptance } from './generated/native-contract.js';

export interface HostInstallRequest {
  detailToken: string;
  targetToken: string;
  requestKey: string;
  hostInstanceId: string;
  mode: 'new' | 'retry';
  initiatedFrom: 'plaza' | 'conversation';
}

export interface SkillHubConfig {
  registryUrl: string;
  detailTabs: { versions: TabMode; securityAudit: TabMode; reviews: TabMode };
}

export interface CatalogContext {
  sourceRevision: string;
  registryUrl: string;
  configured: boolean;
  detailTabs: { versions: TabMode; securityAudit: TabMode; reviews: TabMode };
}

export interface SkillList {
  items: { namespace: string; slug: string; latestVersion: string; summary: string;
    displayName?: string; downloadCount?: number; starCount?: number; ratingAvg?: number; ratingCount?: number }[];
  total: number;
  returned: number;
  limit: number;
}

export interface CatalogLabel { slug: string; type: 'RECOMMENDED' | 'PRIVILEGED'; displayName: string }
export interface CatalogLabels { available: boolean; items: CatalogLabel[] }

export type CatalogSort = 'newest' | 'downloads' | 'rating';
export interface CatalogPageResult {
  items: SkillList['items'];
  total: number;
  page: number;
  size: number;
  hasMore: boolean;
  mode: 'paged' | 'limited';
}

/** Read-only fixed-version content; it is not an installation authorization. */
export interface SkillDetail {
  detailToken: string;
  title: string;
  summary: string;
  downloadCount?: number;
  starCount?: number;
  ratingAvg?: number;
  ratingCount?: number;
  tabs: { versions: ModuleAvailability; securityAudit: ModuleAvailability; reviews: ModuleAvailability };
  namespace: string;
  slug: string;
  version: string;
  versionId: number;
  documentation: { state: 'ready'; content: string }
    | { state: 'error'; reason: string; retryable: boolean };
}

export interface ConversationDetail extends SkillDetail { selectionRef: string }

export interface InstallationTargetList {
  hostInstanceId: string;
  targets: { targetToken: string; scope: 'user' | 'project' }[];
  unavailable: ('user' | 'project')[];
}

export interface InstalledSkillList {
  items: { itemKey: string; namespace: string; slug: string; version: string; scope: 'user' | 'project';
    state: 'present' | 'missing' | 'metadata-invalid' | 'mismatch' | 'unreadable' }[];
  /** Completeness of the inventory projection; unavailable roots are reported independently. */
  complete: boolean;
  issues: string[];
  unavailable: ('user' | 'project')[];
}

export interface InstalledSkillDetail {
  item: InstalledSkillList['items'][number];
  content: { state: 'ready'; detail: SkillDetail } | { state: 'local-only'; reason: string };
}

export interface InstalledUpdateCheck {
  state: 'upgradable' | 'current' | 'blocked' | 'unavailable';
  currentVersion: string;
  candidateVersion: string | null;
  reason: string;
  checkedAt: string;
}

export interface UninstallPlanView {
  hostInstanceId: string;
  status: 'ready' | 'blocked';
  reason: string;
  kind: 'uninstall' | 'remove-stale-record' | null;
  planId: string | null;
  expiresAt: string | null;
  currentVersion: string;
  target: { scope: 'user' | 'project'; path: string } | null;
}

export type ModuleAvailability = 'hidden' | 'available' | 'anonymous-denied' | 'unknown';
export interface VersionPage {
  items: { version: string; changelog: string | null; publishedAt: string | null; downloadAvailable: boolean }[];
  total: number; page: number; size: number;
}
export interface ReviewPage {
  items: { id: number; displayName: string; score: number; reviewText: string; createdAt: string }[];
  total: number; page: number; size: number;
}

declare module '@deepseek-ai/dsh-typert-protocol' {
  interface RemoteErrorDetailsMap {
    'skillhub/source-not-configured': { retryable: false };
    'skillhub/context-stale': { retryable: false };
    'skillhub/installation-unavailable': { retryable: boolean };
    'skillhub/target-unavailable': { retryable: boolean };
    'skillhub/invalid-installation-request': { retryable: false };
    'skillhub/request-conflict': { retryable: false };
    'skillhub/registry-error': { reason: string; retryable: boolean; status?: number; retryAfterSeconds?: number };
  }
}

/** A read-only directory probe; it does not establish download or installation availability. */
export interface RegistryConnectionTest {
  reachable: true;
  mode: 'paged' | 'limited';
  elapsedMs: number;
}
