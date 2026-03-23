import { NotificationPreferenceForm } from '@/features/notification/notification-preference-form'

/**
 * Settings page for managing notification preferences at /settings/notifications.
 */
export function NotificationSettingsPage() {
  return (
    <div className="mx-auto max-w-2xl">
      <NotificationPreferenceForm />
    </div>
  )
}
