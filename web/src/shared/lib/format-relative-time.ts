import i18n from '@/i18n/config'

/** Formats a timestamp for skill cards and notification UI in the selected language. */
export function formatRelativeTime(dateStr: string, lang: string): string {
  const diff = Date.now() - new Date(dateStr).getTime()
  const minutes = Math.floor(diff / 60_000)
  const hours = Math.floor(diff / 3_600_000)
  const days = Math.floor(diff / 86_400_000)
  const t = i18n.getFixedT(lang)

  if (minutes < 1) return t('relativeTime.justNow')
  if (minutes < 60) return t('relativeTime.minutes', { count: minutes })
  if (hours < 24) return t('relativeTime.hours', { count: hours })
  if (days < 30) return t('relativeTime.days', { count: days })
  return new Date(dateStr).toLocaleDateString(lang)
}
