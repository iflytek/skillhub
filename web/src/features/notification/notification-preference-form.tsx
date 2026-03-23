import { useTranslation } from 'react-i18next'
import type { NotificationPreferenceItem } from '@/api/types'
import { useNotificationPreferences, useUpdateNotificationPreferences } from './use-notification-preferences'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'

const CATEGORIES = ['PUBLISH', 'REVIEW', 'PROMOTION', 'REPORT'] as const
type Category = (typeof CATEGORIES)[number]

const CATEGORY_KEYS: Record<Category, { label: string; desc: string }> = {
  PUBLISH: { label: 'notification.preferences.publish', desc: 'notification.preferences.publishDesc' },
  REVIEW: { label: 'notification.preferences.review', desc: 'notification.preferences.reviewDesc' },
  PROMOTION: { label: 'notification.preferences.promotion', desc: 'notification.preferences.promotionDesc' },
  REPORT: { label: 'notification.preferences.report', desc: 'notification.preferences.reportDesc' },
}

function getEnabled(preferences: NotificationPreferenceItem[], category: string): boolean {
  const item = preferences.find((p) => p.category === category && p.channel === 'IN_APP')
  // Default to true when no record exists
  return item?.enabled ?? true
}

function buildUpdatedPreferences(
  current: NotificationPreferenceItem[],
  category: string,
  enabled: boolean,
): NotificationPreferenceItem[] {
  const existing = current.find((p) => p.category === category && p.channel === 'IN_APP')
  if (existing) {
    return current.map((p) =>
      p.category === category && p.channel === 'IN_APP' ? { ...p, enabled } : p,
    )
  }
  return [...current, { category, channel: 'IN_APP', enabled }]
}

/**
 * Renders the notification preference toggles for all supported categories.
 */
export function NotificationPreferenceForm() {
  const { t } = useTranslation()
  const { data: preferences = [], isLoading } = useNotificationPreferences()
  const { mutate: updatePreferences, isPending } = useUpdateNotificationPreferences()

  function handleToggle(category: string) {
    const current = getEnabled(preferences, category)
    const updated = buildUpdatedPreferences(preferences, category, !current)
    updatePreferences(updated)
  }

  return (
    <Card className="glass-strong">
      <CardHeader>
        <CardTitle>{t('notification.preferences.title')}</CardTitle>
        <CardDescription>{t('notification.preferences.description')}</CardDescription>
      </CardHeader>
      <CardContent>
        <div className="divide-y divide-border">
          {CATEGORIES.map((category) => {
            const enabled = getEnabled(preferences, category)
            const keys = CATEGORY_KEYS[category]
            const toggleId = `pref-toggle-${category}`

            return (
              <div key={category} className="flex items-center justify-between py-4 first:pt-0 last:pb-0">
                <div className="space-y-0.5">
                  <label htmlFor={toggleId} className="text-sm font-medium cursor-pointer">
                    {t(keys.label)}
                  </label>
                  <p className="text-xs text-muted-foreground">{t(keys.desc)}</p>
                </div>

                {/* Accessible toggle switch */}
                <button
                  id={toggleId}
                  role="switch"
                  aria-checked={enabled}
                  disabled={isLoading || isPending}
                  onClick={() => handleToggle(category)}
                  className={[
                    'relative inline-flex h-6 w-11 shrink-0 cursor-pointer rounded-full border-2 border-transparent',
                    'transition-colors duration-200 ease-in-out focus-visible:outline-none focus-visible:ring-2',
                    'focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
                    enabled ? 'bg-primary' : 'bg-input',
                  ].join(' ')}
                >
                  <span
                    aria-hidden="true"
                    className={[
                      'pointer-events-none inline-block h-5 w-5 rounded-full bg-background shadow-lg',
                      'ring-0 transition duration-200 ease-in-out',
                      enabled ? 'translate-x-5' : 'translate-x-0',
                    ].join(' ')}
                  />
                </button>
              </div>
            )
          })}
        </div>
      </CardContent>
    </Card>
  )
}
