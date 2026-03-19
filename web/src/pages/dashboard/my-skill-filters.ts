export type MySkillFilter = 'ALL' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'ARCHIVED' | 'HIDDEN'

export function getMySkillFilters(isSuperAdmin: boolean): MySkillFilter[] {
  if (isSuperAdmin) {
    return ['ALL', 'PUBLISHED', 'HIDDEN', 'ARCHIVED']
  }
  return ['ALL', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED']
}

export function getMySkillEmptyStateKey(filter: MySkillFilter): { title: string; description: string } {
  if (filter === 'ALL') {
    return {
      title: 'mySkills.emptyTitle',
      description: 'mySkills.emptyDescription',
    }
  }

  return {
    title: 'mySkills.emptyFilteredTitle',
    description: `mySkills.emptyFilteredDescription.${filter}`,
  }
}
