import { fetchJson, getCsrfHeaders } from './client'
import type { components } from './generated/schema'
import type { SkillSummary } from './types'

export type RecommendationContent = components['schemas']['RecommendationContent']
export type RecommendationTopic = components['schemas']['RecommendationTopic']
export type RecommendationAdmin = components['schemas']['RecommendationAdminResponse']
export type RecommendationPublic = components['schemas']['RecommendationPublicResponse']
export type RecommendationTopicView = components['schemas']['RecommendationTopicResponse']
export type RecommendedSkill = SkillSummary & { labels?: { slug: string; displayName: string }[] }
type Summary = components['schemas']['SkillSummaryResponse']
const adminPath = '/api/v1/admin/system-config/recommendations'
const json = (body: unknown) => ({ headers: getCsrfHeaders({ 'Content-Type': 'application/json' }), body: JSON.stringify(body) })
export const recommendationApi = {
  get: () => fetchJson<RecommendationPublic>('/api/v1/site-recommendations'),
  topic: (id: string) => fetchJson<RecommendationTopicView>(`/api/v1/site-recommendations/topics/${encodeURIComponent(id)}`),
  admin: () => fetchJson<RecommendationAdmin>(adminPath),
  save: (version: number, draft: RecommendationContent) => fetchJson<RecommendationAdmin>(adminPath, { method: 'PUT', ...json({ version, draft }) }),
  publish: (version: number) => fetchJson<RecommendationAdmin>(`${adminPath}/publish`, { method: 'POST', ...json({ version }) }),
  candidates: (q: string, page: number, label = '') => fetchJson<components['schemas']['PageResponseSkillSummaryResponse']>(`${adminPath}/candidates?${new URLSearchParams({ q, page: String(page), size: '20', label })}`),
}

export type RecommendationDisplay = {
  topics: { id: string; title: string; description: string; coverUrl?: string | null; coverStyle?: RecommendationTopic['coverStyle']; skillCount: number; skills: RecommendedSkill[] }[]
  featuredSkills: RecommendedSkill[]
}
function version(value: components['schemas']['SkillLifecycleVersionResponse'] | undefined) {
  return value && typeof value.id === 'number' && value.version && value.status
    ? { id: value.id, version: value.version, status: value.status, displayName: value.displayName, summary: value.summary, syncRevision: value.syncRevision } : undefined
}
/** Adapt optional OpenAPI fields only after checking the card's identity requirements. */
export function recommendationSkills(values: Summary[] | undefined): RecommendedSkill[] {
  return (values ?? []).flatMap(s => typeof s.id === 'number' && !!s.slug && !!s.namespace && !!s.displayName ? [{
    id: s.id, slug: s.slug, namespace: s.namespace, displayName: s.displayName,
    summary: s.summary ?? undefined, downloadCount: s.downloadCount ?? 0, starCount: s.starCount ?? 0,
    ratingCount: s.ratingCount ?? 0, ratingAvg: s.ratingAvg, updatedAt: s.updatedAt ?? '', canSubmitPromotion: false,
    labels: s.labels?.flatMap(label => label.slug && label.displayName ? [{ slug: label.slug, displayName: label.displayName }] : []),
    avatarUrl: s.avatarUrl, ownerDisplayName: s.ownerDisplayName, ownerId: s.ownerId,
    visibility: s.visibility, status: s.status,
    headlineVersion: version(s.headlineVersion), publishedVersion: version(s.publishedVersion),
  }] : [])
}
export function recommendationDisplay(value: RecommendationPublic): RecommendationDisplay {
  return { topics: (value.topics ?? []).flatMap(topic => topic.id && topic.title ? [{
    id: topic.id, title: topic.title, description: topic.description ?? '', coverUrl: topic.coverUrl, coverStyle: topic.coverStyle,
    skillCount: topic.skillCount ?? 0, skills: recommendationSkills(topic.skills),
  }] : []), featuredSkills: recommendationSkills(value.featuredSkills) }
}
export const RECOMMENDATION_KEY = ['site-recommendations'] as const
export const emptyRecommendations = (): RecommendationContent => ({ topics: [], featuredSkillIds: [] })
