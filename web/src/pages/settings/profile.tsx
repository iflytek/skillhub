import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, profileApi } from '@/api/client'
import { useAuth } from '@/features/auth/use-auth'
import { truncateErrorMessage } from '@/shared/lib/error-display'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

/** Regex matching allowed display name characters: Chinese, English, digits, spaces, underscore, hyphen. */
const DISPLAY_NAME_PATTERN = /^[\u4e00-\u9fa5a-zA-Z0-9_ -]+$/

type FieldValidator = (value: string, t: (key: string) => string) => string | null

const FIELD_VALIDATORS: Record<string, FieldValidator> = {
  displayName: (value, t) => {
    const trimmed = value.trim()
    if (trimmed.length < 2 || trimmed.length > 32) return t('profile.validation.length')
    if (!DISPLAY_NAME_PATTERN.test(trimmed)) return t('profile.validation.pattern')
    return null
  },
}

/** Map field key to the profile data property holding its current value. */
function getFieldValue(
  field: string,
  profile: { displayName: string; avatarUrl: string | null; email: string | null },
): string {
  switch (field) {
    case 'displayName': return profile.displayName ?? ''
    case 'email': return profile.email ?? ''
    default: return ''
  }
}

export function ProfileSettingsPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const [isEditing, setIsEditing] = useState(false)
  const [formValues, setFormValues] = useState<Record<string, string>>({})
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [isSubmitting, setIsSubmitting] = useState(false)

  const { data: profileData } = useQuery({
    queryKey: ['profile'],
    queryFn: () => profileApi.getProfile(),
    staleTime: 30_000,
  })

  const pendingChanges = profileData?.pendingChanges
  const fieldPolicies = profileData?.fieldPolicies ?? {}
  const effectiveDisplayName = profileData?.displayName ?? user?.displayName ?? ''
  const effectiveAvatarUrl = profileData?.avatarUrl ?? user?.avatarUrl ?? null
  const effectiveEmail = profileData?.email ?? user?.email ?? ''

  const effectiveProfile = {
    displayName: effectiveDisplayName,
    avatarUrl: effectiveAvatarUrl,
    email: effectiveEmail,
  }

  const hasEditableFields = Object.values(fieldPolicies).some((p) => p.editable)

  function handleEdit() {
    const values: Record<string, string> = {}
    for (const field of Object.keys(fieldPolicies)) {
      values[field] = getFieldValue(field, effectiveProfile)
    }
    setFormValues(values)
    setErrors({})
    setIsEditing(true)
  }

  function handleCancel() {
    setIsEditing(false)
    setErrors({})
  }

  function handleFieldChange(field: string, value: string) {
    setFormValues((prev) => ({ ...prev, [field]: value }))
    setErrors((prev) => {
      const next = { ...prev }
      delete next[field]
      return next
    })
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrors({})

    // Collect changed fields only
    const changes: Record<string, string> = {}
    for (const [field, policy] of Object.entries(fieldPolicies)) {
      if (!policy.editable) continue
      const newVal = (formValues[field] ?? '').trim()
      const oldVal = getFieldValue(field, effectiveProfile)
      if (newVal !== oldVal) {
        changes[field] = newVal
      }
    }

    if (Object.keys(changes).length === 0) {
      toast.success(t('profile.noChanges'))
      return
    }

    // Validate
    const newErrors: Record<string, string> = {}
    for (const field of Object.keys(changes)) {
      const validator = FIELD_VALIDATORS[field]
      if (validator) {
        const err = validator(changes[field], t)
        if (err) newErrors[field] = err
      }
    }
    if (Object.keys(newErrors).length > 0) {
      setErrors(newErrors)
      return
    }

    setIsSubmitting(true)
    try {
      const result = await profileApi.updateProfile(changes)

      if (result.status === 'PENDING_REVIEW') {
        queryClient.setQueryData(['profile'], (current: typeof profileData) => {
          if (!current) return current
          return {
            ...current,
            pendingChanges: {
              status: 'PENDING',
              changes,
              reviewComment: null,
              createdAt: new Date().toISOString(),
            },
          }
        })
        await queryClient.invalidateQueries({ queryKey: ['profile'] })
        toast.success(t('profile.pendingReviewTitle'), t('profile.pendingReviewDescription'))
      } else if (result.status === 'PARTIALLY_APPLIED') {
        await queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
        await queryClient.invalidateQueries({ queryKey: ['profile'] })
        toast.success(t('profile.partiallyAppliedTitle'), t('profile.partiallyAppliedDescription'))
      } else {
        toast.success(t('profile.successTitle'), t('profile.successDescription'))
        await queryClient.invalidateQueries({ queryKey: ['auth', 'me'] })
        await queryClient.invalidateQueries({ queryKey: ['profile'] })
      }

      setIsEditing(false)
    } catch (error) {
      if (error instanceof ApiError) {
        setErrors({ _form: truncateErrorMessage(error.message) ?? t('profile.defaultError') })
      } else {
        setErrors({ _form: t('profile.defaultError') })
      }
    } finally {
      setIsSubmitting(false)
    }
  }

  // Check if any changed field requires review
  const hasReviewFields = isEditing && Object.entries(fieldPolicies).some(([field, policy]) => {
    if (!policy.editable || !policy.requiresReview) return false
    const newVal = (formValues[field] ?? '').trim()
    const oldVal = getFieldValue(field, effectiveProfile)
    return newVal !== oldVal
  })

  return (
    <div className="mx-auto max-w-2xl">
      <Card className="glass-strong">
        <CardHeader className="flex flex-row items-center justify-between">
          <div>
            <CardTitle>{t('profile.title')}</CardTitle>
            <CardDescription>{t('profile.subtitle')}</CardDescription>
          </div>
          {!isEditing ? (
            <div className="flex items-center gap-2">
              <Button type="button" variant="outline" size="sm" onClick={() => void navigate({ to: '/reset-password' })}>
                {t('profile.resetPassword')}
              </Button>
              {hasEditableFields ? (
                <Button type="button" variant="outline" size="sm" onClick={handleEdit}>
                  {t('profile.edit')}
                </Button>
              ) : null}
            </div>
          ) : null}
        </CardHeader>
        <CardContent className="space-y-6">
          {/* Avatar (read-only) */}
          {effectiveAvatarUrl ? (
            <div className="flex items-center gap-4">
              <img
                src={effectiveAvatarUrl}
                alt={effectiveDisplayName}
                className="h-16 w-16 rounded-2xl border-2 border-border/60 shadow-card"
              />
            </div>
          ) : null}

          <div className="space-y-2">
            <label className="text-sm font-medium">{t('profile.userId')}</label>
            <p className="text-sm text-muted-foreground">{user?.userId || '-'}</p>
          </div>

          <form className="space-y-4" onSubmit={handleSubmit}>
            {/* Dynamic fields */}
            {Object.entries(fieldPolicies).map(([field, policy]) => {
              const label = t(`profile.${field}`)
              const currentValue = getFieldValue(field, effectiveProfile)

              return (
                <div key={field} className="space-y-2">
                  <label className="text-sm font-medium" htmlFor={`field-${field}`}>
                    {label}
                  </label>

                  {isEditing && policy.editable ? (
                    <Input
                      id={`field-${field}`}
                      type="text"
                      maxLength={field === 'displayName' ? 32 : undefined}
                      value={formValues[field] ?? ''}
                      onChange={(e) => handleFieldChange(field, e.target.value)}
                      autoFocus={field === 'displayName'}
                    />
                  ) : (
                    <p className={`text-sm ${policy.editable ? '' : 'text-muted-foreground'}`}>
                      {currentValue || '-'}
                    </p>
                  )}

                  {errors[field] ? (
                    <p className="text-sm text-red-600">{errors[field]}</p>
                  ) : null}
                </div>
              )
            })}

            {errors._form ? <p className="text-sm text-red-600">{errors._form}</p> : null}

            {/* Review hint */}
            {hasReviewFields ? (
              <p className="text-sm text-muted-foreground">{t('profile.reviewHint')}</p>
            ) : null}

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

          {/* Review status banner */}
          {pendingChanges?.status === 'PENDING' ? (
            <div className="rounded-lg border border-yellow-500/30 bg-yellow-500/10 p-3 text-sm text-yellow-700 dark:text-yellow-400">
              {t('profile.pendingReview', { name: pendingChanges.changes?.displayName })}
            </div>
          ) : null}
          {pendingChanges?.status === 'REJECTED' ? (
            <div className="rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-400">
              <p>{t('profile.rejected')}</p>
              {pendingChanges.reviewComment ? (
                <p className="mt-1 text-xs opacity-80">{t('profile.rejectedReason', { reason: pendingChanges.reviewComment })}</p>
              ) : null}
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  )
}
