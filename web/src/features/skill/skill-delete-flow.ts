import { normalizeSkillDetailReturnTo } from '@/shared/lib/skill-navigation'

export function isDeleteSlugConfirmationValid(expectedSlug: string, typedSlug: string) {
  return typedSlug === expectedSlug
}

export function resolveDeletedSkillReturnTo(returnTo?: string) {
  return normalizeSkillDetailReturnTo(returnTo) ?? '/search'
}
