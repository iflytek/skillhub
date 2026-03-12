import type { components } from './generated/schema'

export type User = components['schemas']['User']
export type OAuthProvider = components['schemas']['OAuthProvider']
export type ApiToken = components['schemas']['ApiToken']
export type CreateTokenRequest = components['schemas']['CreateTokenRequest']
export type CreateTokenResponse = components['schemas']['CreateTokenResponse']

// Namespace types
export interface Namespace {
  id: number
  slug: string
  displayName: string
  description?: string
  type: 'GLOBAL' | 'TEAM'
  avatarUrl?: string
  status: string
  createdAt: string
  updatedAt?: string
}

export interface NamespaceMember {
  id: number
  userId: string
  role: string
  createdAt: string
}

// Skill types
export interface SkillSummary {
  id: number
  slug: string
  displayName: string
  summary?: string
  downloadCount: number
  starCount: number
  ratingAvg?: number
  ratingCount: number
  latestVersion?: string
  namespace: string
  updatedAt: string
}

export interface SkillDetail {
  id: number
  slug: string
  displayName: string
  summary?: string
  visibility: string
  status: string
  downloadCount: number
  starCount: number
  latestVersion?: string
  namespace: string
}

export interface SkillVersion {
  id: number
  version: string
  status: string
  changelog?: string
  fileCount: number
  totalSize: number
  publishedAt: string
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
