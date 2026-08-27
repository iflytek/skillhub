import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ApiError, authApi } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { clearSessionScopedQueries } from '@/features/notification/notification-session'
import { truncateErrorMessage } from '@/shared/lib/error-display'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

interface PasswordChangeCapabilityUser {
  canChangePassword?: boolean
}

function canUsePasswordChangeForm(user?: PasswordChangeCapabilityUser | null) {
  return user?.canChangePassword === true
}

/**
 * Security settings page for password changes. After a successful change the
 * user is logged out so all existing authenticated state is re-established with
 * the new credential.
 */
export function SecuritySettingsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const canChangePassword = canUsePasswordChangeForm(user)

  /**
   * Submits the password change request and clears local auth state afterward,
   * even if the explicit logout request fails.
   */
  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage('')

    if (!canChangePassword) {
      setErrorMessage(t('security.unavailableTitle'))
      return
    }

    if (!currentPassword.trim()) {
      setErrorMessage(t('security.currentPasswordRequired'))
      return
    }

    if (!newPassword.trim()) {
      setErrorMessage(t('security.newPasswordRequired'))
      return
    }

    setIsSubmitting(true)
    try {
      await authApi.changePassword({ currentPassword, newPassword })
      toast.success(t('security.successTitle'), t('security.successDescription'))
      setCurrentPassword('')
      setNewPassword('')
      try {
        await authApi.logout()
      } catch (error) {
        console.error('Logout after password change failed:', error)
      } finally {
        clearSessionScopedQueries(queryClient)
        queryClient.setQueryData(['auth', 'me'], null)
      }
      await navigate({ to: '/login' })
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        setErrorMessage(t('security.invalidCurrentPassword'))
      } else {
        setErrorMessage(
          truncateErrorMessage(error instanceof Error ? error.message : t('security.defaultError')) ?? t('security.defaultError'),
        )
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Card className="glass-strong">
        <CardHeader>
          <CardTitle>{t('security.title')}</CardTitle>
          <CardDescription>{t('security.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent>
          {canChangePassword ? (
            <form className="space-y-4" onSubmit={handleSubmit}>
              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor="current-password">{t('security.currentPassword')}</label>
                <Input
                  id="current-password"
                  type="password"
                  autoComplete="current-password"
                  value={currentPassword}
                  onChange={(event) => setCurrentPassword(event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <label className="text-sm font-medium" htmlFor="new-password">{t('security.newPassword')}</label>
                <Input
                  id="new-password"
                  type="password"
                  autoComplete="new-password"
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                />
              </div>
              {errorMessage ? <p className="text-sm text-red-600">{errorMessage}</p> : null}
              <Button type="submit" disabled={isSubmitting}>
                {isSubmitting ? t('security.submitting') : t('security.submit')}
              </Button>
            </form>
          ) : (
            <div className="rounded-lg border border-border/70 bg-muted/30 p-4">
              <p className="text-sm font-medium text-foreground">{t('security.unavailableTitle')}</p>
              <p className="mt-2 text-sm text-muted-foreground">{t('security.unavailableDescription')}</p>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
