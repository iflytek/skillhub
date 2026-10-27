import i18n from '@/i18n/config'
import type { NotificationItem } from '@/api/types'

export type NotificationDisplay = {
  title: string
  description: string
}

type NotificationBody = {
  skillName?: string
  version?: string
}

function parseBody(bodyJson?: string): NotificationBody {
  if (!bodyJson) {
    return {}
  }
  try {
    const parsed = JSON.parse(bodyJson)
    return typeof parsed === 'object' && parsed !== null ? parsed as NotificationBody : {}
  } catch {
    return {}
  }
}

const localizedEvents = new Set([
  'REVIEW_SUBMITTED', 'REVIEW_APPROVED', 'REVIEW_REJECTED',
  'PROMOTION_SUBMITTED', 'PROMOTION_APPROVED', 'PROMOTION_REJECTED',
  'REPORT_SUBMITTED', 'REPORT_RESOLVED', 'SKILL_PUBLISHED',
  'SUBSCRIPTION_NEW_VERSION', 'SUBSCRIPTION_VERSION_YANKED',
])

export function resolveNotificationDisplay(item: NotificationItem, language: string): NotificationDisplay {
  const zh = language.toLowerCase().startsWith('zh')
  const t = i18n.getFixedT(language)
  const body = parseBody(item.bodyJson)
  const skillName = body.skillName ?? ''
  const version = body.version ?? ''
  const versionSuffix = version ? (zh ? `（${version}）` : ` (${version})`) : ''

  if (!localizedEvents.has(item.eventType)) {
    return { title: item.title, description: '' }
  }

  return {
    title: t(`notificationEvents.${item.eventType}.title`),
    description: skillName
      ? t(`notificationEvents.${item.eventType}.description`, { skillName, versionSuffix })
      : '',
  }
}
