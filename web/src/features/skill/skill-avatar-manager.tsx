import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { buildApiUrl, skillAvatarApi } from '@/api/client'
import { Button } from '@/shared/ui/button'

export function SkillAvatarManager({ namespace, slug, disabled = false, previewOnly = false }: { namespace: string; slug: string; disabled?: boolean; previewOnly?: boolean }) {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const key = ['skill-avatar', namespace, slug]
  const status = useQuery({ queryKey: key, queryFn: () => skillAvatarApi.status(namespace, slug), enabled: !!namespace && !!slug, staleTime: 0 })
  const [error, setError] = useState('')
  const mutation = useMutation({
    mutationFn: async (action: { kind: 'appeal' | 'retry'; id: number }) => action.kind === 'appeal'
      ? skillAvatarApi.appeal(namespace, slug, action.id)
      : skillAvatarApi.retry(namespace, slug, action.id),
    onSuccess: async () => {
      setError('')
      await Promise.all([queryClient.invalidateQueries({ queryKey: key }), queryClient.invalidateQueries({ queryKey: ['skill'] }), queryClient.invalidateQueries({ queryKey: ['skills'] }), queryClient.invalidateQueries({ queryKey: ['reviews'] })])
    },
    onError: (cause) => setError(cause instanceof Error ? cause.message : t('skillAvatar.failed')),
  })
  const [failedPreview, setFailedPreview] = useState<string | null>(null)
  const candidate = status.data
  const retryCoolingDown = !!candidate?.machineNextRetryAt && Date.parse(candidate.machineNextRetryAt) > Date.now()
  const loadError = status.isError ? <div role="alert" className="flex flex-wrap items-center gap-2 text-xs text-destructive">
    <span>{t(candidate ? 'skillAvatar.statusRefreshFailed' : 'skillAvatar.statusLoadFailed')}</span>
    <Button data-analytics-id="skill.avatar.refresh" type="button" size="sm" variant="ghost" disabled={status.isFetching} onClick={() => void status.refetch()}>{t('skillAvatar.reloadStatus')}</Button>
  </div> : null
  if (!candidate || !['PENDING', 'REJECTED'].includes(candidate.candidateStatus ?? '')) return loadError
  return (
    <div className={`space-y-2 rounded-lg border bg-muted/20 p-3 text-sm ${previewOnly ? 'mb-3' : ''}`} aria-label={t('skillAvatar.submittedImage')}>
      <div className="flex items-center gap-3">
        {candidate.candidateUrl && failedPreview !== candidate.candidateUrl
          ? <img src={buildApiUrl(candidate.candidateUrl)} alt={t('skillAvatar.submittedImage')} className="h-12 w-12 shrink-0 rounded-lg object-cover" onError={() => setFailedPreview(candidate.candidateUrl)} />
          : <span className="text-xs text-muted-foreground">{t('skillAvatar.candidateUnavailable')}</span>}
        <div className="min-w-0">
          <p className="font-medium">{t('skillAvatar.submittedImage')}</p>
          <p className="text-xs text-muted-foreground">{t('skillAvatar.publicUnchanged')}</p>
        </div>
      </div>
      {candidate?.candidateStatus && <p role="status" className="text-sm">{t('skillAvatar.status')}: {t(`skillAvatar.states.${candidate.candidateStatus}`)}</p>}
      {candidate?.reviewReason && <p className="text-sm">{candidate.reviewReason}</p>}
      {candidate?.machineStatus === 'ERROR' && retryCoolingDown && <p className="text-sm text-muted-foreground">{t('skillAvatar.retryAfter', { time: new Date(candidate.machineNextRetryAt!).toLocaleString(i18n.resolvedLanguage || i18n.language) })}</p>}
      {candidate?.machineStatus === 'ERROR' && candidate.automaticRetryExhausted && <p className="text-sm text-muted-foreground">{t('skillAvatar.automaticRetryStopped')}</p>}
      {!previewOnly && <div className="flex flex-wrap gap-2">
        {candidate?.candidateStatus === 'PENDING' && candidate.machineStatus === 'BLOCKED'
          && candidate.reviewStatus === 'NOT_REQUIRED' && candidate.candidateId
          && <Button data-analytics-id="skill.avatar.appeal" variant="outline" disabled={disabled || mutation.isPending} onClick={() => mutation.mutate({ kind: 'appeal', id: candidate.candidateId! })}>{t('skillAvatar.appeal')}</Button>}
        {candidate?.machineStatus === 'ERROR' && candidate.candidateId && <Button data-analytics-id="skill.avatar.retry" variant="outline" disabled={disabled || mutation.isPending || retryCoolingDown} onClick={() => mutation.mutate({ kind: 'retry', id: candidate.candidateId! })}>{t('skillAvatar.retry')}</Button>}
      </div>}
      {loadError}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </div>
  )
}
