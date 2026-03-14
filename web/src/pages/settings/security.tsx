import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { authApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

export function SecuritySettingsPage() {
  const { t } = useTranslation()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [statusMessage, setStatusMessage] = useState('')
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatusMessage('')
    setErrorMessage('')
    setIsSubmitting(true)
    try {
      await authApi.changePassword({ currentPassword, newPassword })
      setStatusMessage(t('security.success'))
      setCurrentPassword('')
      setNewPassword('')
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : t('security.defaultError'))
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
            {statusMessage ? <p className="text-sm text-emerald-600">{statusMessage}</p> : null}
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
