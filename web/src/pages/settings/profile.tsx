import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { ApiError, profileApi } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { truncateErrorMessage } from '@/shared/lib/error-display'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

/** Regex matching allowed display name characters: Chinese, English, digits, underscore, hyphen. */
const DISPLAY_NAME_PATTERN = /^[\u4e00-\u9fa5a-zA-Z0-9_-]+$/

export function ProfileSettingsPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const queryClient = useQueryClient()

  const [isEditing, setIsEditing] = useState(false)
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [errorMessage, setErrorMessage] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  function handleEdit() {
    setDisplayName(user?.displayName ?? '')
    setErrorMessage('')
    setIsEditing(true)
  }

  function handleCancel() {
    setIsEditing(false)
    setErrorMessage('')
  }

  /** Client-side validation before submitting. */
  function validate(value: string): string | null {
    const trimmed = value.trim()
    if (trimmed.length < 2 || trimmed.length > 32) {
      return t('profile.validation.length')
    }
    if (!DISPLAY_NAME_PATTERN.test(trimmed)) {
      return t('profile.validation.pattern')
    }
    return null
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage('')

    const trimmed = displayName.trim()
    const validationError = validate(trimmed)
    if (validationError) {
      setErrorMessage(validationError)
      return
    }

    setIsSubmitting(true)
    try {
      const result = await profileApi.updateProfile({ displayName: trimmed })

      if (result.status === 'PENDING_REVIEW') {
        toast.success(t('profile.pendingReviewTitle'), t('profile.pendingReviewDescription'))
      } else {
        toast.success(t('profile.successTitle'), t('profile.successDescription'))
        // Refresh auth cache so the header and other components pick up the new name
        await queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
      }

      setIsEditing(false)
    } catch (error) {
      if (error instanceof ApiError) {
        setErrorMessage(
          truncateErrorMessage(error.message) ?? t('profile.defaultError'),
        )
      } else {
        setErrorMessage(t('profile.defaultError'))
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-2xl">
      <Card className="glass-strong">
        <CardHeader>
          <CardTitle>{t('profile.title')}</CardTitle>
          <CardDescription>{t('profile.subtitle')}</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Avatar (read-only for now) */}
          {user?.avatarUrl ? (
            <div className="flex items-center gap-4">
              <img
                src={user.avatarUrl}
                alt={user.displayName}
                className="h-16 w-16 rounded-2xl border-2 border-border/60 shadow-card"
              />
            </div>
          ) : null}

          {/* Display name field */}
          <form className="space-y-4" onSubmit={handleSubmit}>
            <div className="space-y-2">
              <label className="text-sm font-medium" htmlFor="display-name">
                {t('profile.displayName')}
              </label>

              {isEditing ? (
                <Input
                  id="display-name"
                  type="text"
                  maxLength={32}
                  value={displayName}
                  onChange={(event) => setDisplayName(event.target.value)}
                  autoFocus
                />
              ) : (
                <div className="flex items-center gap-3">
                  <span className="text-sm">{user?.displayName}</span>
                  <Button type="button" variant="outline" size="sm" onClick={handleEdit}>
                    {t('profile.edit')}
                  </Button>
                </div>
              )}
            </div>

            {errorMessage ? <p className="text-sm text-red-600">{errorMessage}</p> : null}

            {isEditing ? (
              <div className="flex gap-2">
                <Button type="submit" disabled={isSubmitting}>
                  {isSubmitting ? t('profile.saving') : t('profile.save')}
                </Button>
                <Button type="button" variant="outline" onClick={handleCancel} disabled={isSubmitting}>
                  {t('profile.cancel')}
                </Button>
              </div>
            ) : null}
          </form>

          {/* Email (read-only) */}
          <div className="space-y-2">
            <label className="text-sm font-medium">{t('profile.email')}</label>
            <p className="text-sm text-muted-foreground">{user?.email || '-'}</p>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
