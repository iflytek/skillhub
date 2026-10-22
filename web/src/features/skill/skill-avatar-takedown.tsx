import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { skillAvatarApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'

export function SkillAvatarTakedown({ namespace, slug }: { namespace: string; slug: string }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const mutation = useMutation({
    mutationFn: () => skillAvatarApi.takedown(namespace, slug, reason),
    onSuccess: async () => { setError(''); setReason(''); await Promise.all([queryClient.invalidateQueries({ queryKey: ['skill'] }), queryClient.invalidateQueries({ queryKey: ['skills'] })]) },
    onError: (cause) => setError(cause instanceof Error ? cause.message : t('skillAvatar.failed')),
  })
  return <div className="space-y-2 rounded-lg border border-destructive/30 p-4">
    <p className="text-sm font-medium">{t('skillAvatar.takedownHelp')}</p>
    <Input value={reason} onChange={(event) => setReason(event.target.value)} placeholder={t('skillAvatar.reason')} aria-label={t('skillAvatar.reason')} />
    <Button data-analytics-id="skill.avatar.takedown" variant="outline" disabled={!reason.trim() || mutation.isPending} onClick={() => mutation.mutate()}>{t('skillAvatar.takedown')}</Button>
    {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
  </div>
}
