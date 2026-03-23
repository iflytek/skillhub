import i18n from '@/i18n/config'

export function getI18nCacheKey() {
  return i18n.resolvedLanguage || i18n.language || 'en'
}

export function getSkillDetailQueryKey(namespace: string, slug: string) {
  return ['skills', namespace, slug, getI18nCacheKey()] as const
}

export function getVisibleLabelsQueryKey() {
  return ['labels', 'visible', getI18nCacheKey()] as const
}

export function getSkillLabelsQueryKey(namespace: string, slug: string) {
  return ['labels', 'skill', namespace, slug, getI18nCacheKey()] as const
}

export function getAdminLabelDefinitionsQueryKey() {
  return ['labels', 'admin', getI18nCacheKey()] as const
}
