import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { systemConfigApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'

export function AvatarReviewSettingsPanel() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: ['system-config', 'avatar-review'], queryFn: systemConfigApi.getAvatarReview })
  const [draft, setDraft] = useState({ machineEnabled: false, manualEnabled: true })
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  useEffect(() => { if (settings.data) setDraft({ machineEnabled: settings.data.machineEnabled, manualEnabled: settings.data.manualEnabled }) }, [settings.data])
  const update = useMutation({
    mutationFn: systemConfigApi.updateAvatarReview,
    onSuccess: async () => { setError(''); setSaved(true); await queryClient.invalidateQueries({ queryKey: ['system-config', 'avatar-review'] }) },
    onError: (cause) => { setSaved(false); setError(cause instanceof Error ? cause.message : t('skillAvatar.failed')) },
  })
  const dirty = !!settings.data && (settings.data.machineEnabled !== draft.machineEnabled || settings.data.manualEnabled !== draft.manualEnabled)
  return <Card>
    <CardHeader><CardTitle>{t('skillAvatar.settingsTitle')}</CardTitle><p className="text-sm text-muted-foreground">{t('skillAvatar.settingsHelp')}</p></CardHeader>
    <CardContent className="space-y-4">
      {settings.isLoading && <p>{t('skillAvatar.loading')}</p>}
      {settings.isError && <p role="alert">{t('skillAvatar.failed')}</p>}
      {settings.data && <>
        {(['machineEnabled', 'manualEnabled'] as const).map((field) => <label key={field} className="flex items-center justify-between gap-4 rounded-lg border p-4"><span>{t(`skillAvatar.${field}`)}</span><input data-analytics-id={field === 'machineEnabled' ? 'admin.avatar.machine.toggle' : 'admin.avatar.manual.toggle'} type="checkbox" checked={draft[field]} onChange={(event) => { setDraft({ ...draft, [field]: event.target.checked }); setSaved(false) }} /></label>)}
        {!draft.machineEnabled && !draft.manualEnabled && <p role="alert" className="text-sm text-destructive">{t('skillAvatar.noReviewRisk')}</p>}
        <Button data-analytics-id="admin.avatar.settings" disabled={!dirty || update.isPending} onClick={() => update.mutate({ ...draft, version: settings.data!.version })}>{t('skillAvatar.save')}</Button>
      </>}
      {saved && <p role="status" className="text-sm">{t('skillAvatar.saved')}</p>}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
    </CardContent>
  </Card>
}
