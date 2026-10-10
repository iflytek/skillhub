import type { QueryClient } from '@tanstack/react-query'
import type { PagedResponse, SkillDetail, SkillSummary } from '@/api/types'

type SkillIdentity = {
  namespace: string
  slug: string
}

function normalizeNamespace(namespace: string): string {
  return namespace.startsWith('@') ? namespace.slice(1) : namespace
}

function matchesSkill(skill: SkillIdentity, target: SkillIdentity): boolean {
  return normalizeNamespace(skill.namespace) === normalizeNamespace(target.namespace) && skill.slug === target.slug
}

function incrementSummaryDownloadCount(skill: SkillSummary, target: SkillIdentity): SkillSummary {
  if (!matchesSkill(skill, target)) {
    return skill
  }
  return {
    ...skill,
    downloadCount: skill.downloadCount + 1,
  }
}

function incrementDetailDownloadCount(skill: SkillDetail | undefined, target: SkillIdentity): SkillDetail | undefined {
  if (!skill || !matchesSkill(skill, target)) {
    return skill
  }
  return {
    ...skill,
    downloadCount: skill.downloadCount + 1,
  }
}

function incrementSummaryList(
  skills: SkillSummary[] | undefined,
  target: SkillIdentity,
): SkillSummary[] | undefined {
  return skills?.map((skill) => incrementSummaryDownloadCount(skill, target))
}

function incrementPagedSummaryList(
  page: PagedResponse<SkillSummary> | undefined,
  target: SkillIdentity,
): PagedResponse<SkillSummary> | undefined {
  if (!page) {
    return page
  }
  return {
    ...page,
    items: page.items.map((skill) => incrementSummaryDownloadCount(skill, target)),
  }
}

export function incrementSkillDownloadCount(
  queryClient: QueryClient,
  target: SkillIdentity,
): void {
  queryClient.setQueryData<SkillDetail>(
    ['skills', target.namespace, target.slug],
    (current) => incrementDetailDownloadCount(current, target),
  )
  queryClient.setQueryData<SkillSummary[]>(
    ['skills', 'my'],
    (current) => incrementSummaryList(current, target),
  )
  queryClient.setQueryData<SkillSummary[]>(
    ['skills', 'stars'],
    (current) => incrementSummaryList(current, target),
  )
  queryClient.setQueriesData<PagedResponse<SkillSummary>>(
    { queryKey: ['skills', 'search'] },
    (current) => incrementPagedSummaryList(current, target),
  )
}
