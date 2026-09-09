import { NotificationPreferenceForm } from '@/features/notification/notification-preference-form'
import { useTranslation } from 'react-i18next'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'

/**
 * Settings page for managing notification preferences at /settings/notifications.
 */
export function NotificationSettingsPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('notification.preferences.title')} subtitle={t('notification.preferences.description')} />
      <NotificationPreferenceForm showHeader={false} />
    </div>
  )
}
