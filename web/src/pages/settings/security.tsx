import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ApiError, authApi } from '@/api/client'
import { truncateErrorMessage } from '@/shared/lib/error-display'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

/**
 * Security settings page for password changes. After a successful change the
 * user is logged out so all existing authenticated state is re-established with
 * the new credential.
 */
export function SecuritySettingsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  /**
   * Submits the password change request and clears local auth state afterward,
   * even if the explicit logout request fails.
   */
  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage('')

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
        queryClient.setQueryData(['auth', 'me'], null)
      }
      await navigate({ to: '/login', search: { returnTo: '' } })
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
        </CardContent>
      </Card>
    </div>
  )
}
