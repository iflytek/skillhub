import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { systemConfigApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Switch } from '@/shared/ui/switch'

export function AvatarReviewSettingsPanel({ onDirtyChange, onBusyChange }: { onDirtyChange?: (dirty: boolean) => void; onBusyChange?: (busy: boolean) => void }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: ['system-config', 'avatar-review'], queryFn: systemConfigApi.getAvatarReview })
  const [draft, setDraft] = useState({ machineEnabled: false, manualEnabled: true })
  const [error, setError] = useState('')
  const [saved, setSaved] = useState(false)
  const initializedVersion = useRef<number | null>(null)
  useEffect(() => { if (settings.data && initializedVersion.current !== settings.data.version) { initializedVersion.current = settings.data.version; setDraft({ machineEnabled: settings.data.machineEnabled, manualEnabled: settings.data.manualEnabled }) } }, [settings.data])
  const update = useMutation({
    mutationFn: systemConfigApi.updateAvatarReview,
    onSuccess: async () => { setError(''); setSaved(true); await queryClient.invalidateQueries({ queryKey: ['system-config', 'avatar-review'] }) },
    onError: (cause) => { setSaved(false); setError(cause instanceof Error ? cause.message : t('skillAvatar.failed')) },
  })
  const dirty = !!settings.data && (settings.data.machineEnabled !== draft.machineEnabled || settings.data.manualEnabled !== draft.manualEnabled)
  useEffect(() => { onDirtyChange?.(dirty) }, [dirty, onDirtyChange])
  useEffect(() => { onBusyChange?.(update.isPending) }, [update.isPending, onBusyChange])
  return <section className="flex h-full min-w-0 flex-col rounded-xl border p-5">
    <header className="mb-6"><h3 className="text-lg font-semibold">{t('skillAvatar.settingsTitle')}</h3><p className="mt-1 text-sm leading-5 text-muted-foreground">{t('skillAvatar.settingsHelp')}</p></header>
    {settings.isLoading && <p>{t('skillAvatar.loading')}</p>}
    {settings.isError && <p role="alert">{t('skillAvatar.failed')}</p>}
    {settings.data && <>
      <div className="divide-y">{(['machineEnabled', 'manualEnabled'] as const).map(field => <div key={field} className="flex items-center justify-between gap-4 py-4"><div className="space-y-1"><label htmlFor={`avatar-${field}`} className="text-sm font-medium">{t(`skillAvatar.${field}`)}</label><p className="text-xs leading-5 text-muted-foreground">{t(`skillAvatar.${field}Help`)}</p></div><Switch id={`avatar-${field}`} aria-label={t(`skillAvatar.${field}`)} data-analytics-id={field === 'machineEnabled' ? 'admin.avatar.machine.toggle' : 'admin.avatar.manual.toggle'} checked={draft[field]} disabled={update.isPending} onCheckedChange={checked => { setDraft({ ...draft, [field]: checked }); setSaved(false) }} /></div>)}</div>
      {!draft.machineEnabled && !draft.manualEnabled && <p role="alert" className="mb-4 text-sm text-destructive">{t('skillAvatar.noReviewRisk')}</p>}
      {saved && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{t('skillAvatar.saved')}</p>}
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <footer className="mt-auto flex flex-wrap items-center justify-between gap-3 border-t pt-4"><span className="text-xs text-muted-foreground">{t(dirty ? 'systemConfig.unsaved' : 'systemConfig.upToDate')}</span><div className="flex gap-2"><Button variant="outline" disabled={!dirty || update.isPending} onClick={() => { setDraft({ machineEnabled: settings.data!.machineEnabled, manualEnabled: settings.data!.manualEnabled }); setError(''); setSaved(false) }}>{t('systemConfig.discard')}</Button><Button data-analytics-id="admin.avatar.settings" disabled={!dirty || update.isPending} onClick={() => update.mutate({ ...draft, version: settings.data!.version })}>{t(update.isPending ? 'systemConfig.saving' : 'skillAvatar.save')}</Button></div></footer>
    </>}
  </section>
}
