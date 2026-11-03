import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import type { GithubDeepDiscoveryConfiguration, GithubDeepDiscoveryConfigurationInput } from '@/api/client'
import { ApiError } from '@/shared/lib/api-error'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { DEEP_DISCOVERY_QUERY_KEY, useUpdateGithubDeepDiscoveryConfiguration } from './use-github-deep-discovery'

type Exclusion = GithubDeepDiscoveryConfigurationInput['exclusions'][number]
type Budget = GithubDeepDiscoveryConfigurationInput['budgetProfile']

export function GithubDeepDiscoveryConfig({ configuration, onClose, embedded = false }: {
  configuration: GithubDeepDiscoveryConfiguration
  onClose: () => void
  embedded?: boolean
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const update = useUpdateGithubDeepDiscoveryConfiguration()
  const [enabled, setEnabled] = useState(configuration.enabled ?? false)
  const [budgetProfile, setBudgetProfile] = useState<Budget>(configuration.budgetProfile ?? 'BALANCED')
  const [exclusions, setExclusions] = useState<Exclusion[]>(() =>
    (configuration.exclusions ?? []).map((item) => ({ kind: item.kind ?? 'REPOSITORY', value: item.value ?? '' })))
  const [kind, setKind] = useState<Exclusion['kind']>('REPOSITORY')
  const [value, setValue] = useState('')
  const [conflict, setConflict] = useState(false)

  const addExclusion = (event: FormEvent) => {
    event.preventDefault()
    const normalized = value.trim().toLowerCase()
    if (!normalized || exclusions.some((item) => item.kind === kind && item.value.toLowerCase() === normalized)) return
    setExclusions((current) => [...current, { kind, value: normalized }])
    setValue('')
  }

  const save = async () => {
    setConflict(false)
    try {
      await update.mutateAsync({
        expectedVersion: configuration.version!, enabled, budgetProfile, exclusions,
      })
      toast.success(t('deepDiscovery.configSaved'))
      onClose()
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setConflict(true)
      } else {
        toast.error(t('deepDiscovery.configSaveFailed'), error instanceof Error ? error.message : '')
      }
    }
  }

  const content = <>
      <DialogHeader>
        <DialogTitle>{t('deepDiscovery.configure')}</DialogTitle>
        <DialogDescription>{t('deepDiscovery.configDescription')}</DialogDescription>
      </DialogHeader>
      <div className="space-y-5">
        <label className="flex items-start justify-between gap-4 border-b pb-4">
          <span><span className="block font-medium">{t('deepDiscovery.enabled')}</span><span className="mt-1 block text-sm text-muted-foreground">{t('deepDiscovery.enabledHelp')}</span></span>
          <input data-analytics-id="github.discovery.toggle" type="checkbox" checked={enabled} onChange={(event) => setEnabled(event.target.checked)} className="mt-1 h-5 w-5 accent-primary" />
        </label>
        <fieldset className="space-y-3 border-b pb-4">
          <legend className="font-medium">{t('deepDiscovery.budget')}</legend>
          <div className="grid gap-2 sm:grid-cols-3">
            {(['ECONOMY', 'BALANCED', 'ACCELERATED'] as const).map((profile) => <label key={profile}
              className={`flex cursor-pointer items-start gap-2 rounded border p-3 ${budgetProfile === profile ? 'border-primary bg-primary/5' : 'border-border'}`}>
              <input data-analytics-id={profile === 'ECONOMY' ? 'github.discovery.budget.economy' : profile === 'BALANCED' ? 'github.discovery.budget.balanced' : 'github.discovery.budget.accelerated'} type="radio" name="deep-discovery-budget" value={profile} checked={budgetProfile === profile}
                onChange={() => setBudgetProfile(profile)} className="mt-1 accent-primary" />
              <span><span className="block text-sm font-medium">{t(`deepDiscovery.budgets.${profile}`)}</span><span className="block text-xs text-muted-foreground">{t(`deepDiscovery.budgetHelp.${profile}`)}</span></span>
            </label>)}
          </div>
          <p className="text-xs text-muted-foreground">{t('deepDiscovery.budgetScope')}</p>
        </fieldset>
        <div className="space-y-3">
          <h3 className="font-medium">{t('deepDiscovery.exclusions')}</h3>
          <p className="text-sm text-muted-foreground">{t('deepDiscovery.exclusionsHelp')}</p>
          <form data-analytics-id="github.exclusion.add" onSubmit={addExclusion} className="flex flex-wrap gap-2">
            <select data-analytics-id="github.exclusion.kind" aria-label={t('deepDiscovery.exclusionKind')} value={kind} onChange={(event) => setKind(event.target.value as Exclusion['kind'])}
              className="h-9 rounded border bg-background px-2 text-sm">
              <option value="REPOSITORY">{t('deepDiscovery.exclusionRepository')}</option>
              <option value="OWNER">{t('deepDiscovery.exclusionOwner')}</option>
            </select>
            <Input className="min-w-40 flex-1" aria-label={t('deepDiscovery.exclusionValue')} maxLength={140}
              placeholder={kind === 'REPOSITORY' ? 'owner/repository' : 'owner'} value={value}
              onChange={(event) => setValue(event.target.value)} />
            <Button type="submit" size="sm" variant="outline" disabled={!value.trim() || exclusions.length >= 1000}><Plus className="h-4 w-4" />{t('deepDiscovery.add')}</Button>
          </form>
          <div className="max-h-40 space-y-1 overflow-y-auto">
            {exclusions.map((item, index) => <div key={`${item.kind}:${item.value}`} className="flex items-center justify-between gap-2 rounded border px-3 py-2 text-sm">
              <span className="min-w-0 break-all">{t(item.kind === 'OWNER' ? 'deepDiscovery.exclusionOwner' : 'deepDiscovery.exclusionRepository')}: {item.value}</span>
              <Button data-analytics-id="github.exclusion.remove" type="button" size="sm" variant="ghost" aria-label={t('deepDiscovery.removeExclusion', { value: item.value })}
                onClick={() => setExclusions((current) => current.filter((_, at) => at !== index))}><Trash2 className="h-4 w-4" /></Button>
            </div>)}
            {exclusions.length === 0 ? <p className="text-sm text-muted-foreground">{t('deepDiscovery.noExclusions')}</p> : null}
          </div>
        </div>
      </div>
      {conflict ? <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded border border-destructive/40 p-3 text-sm text-destructive"><span>{t('deepDiscovery.configConflict')}</span><Button data-analytics-id="github.discovery.reload" type="button" variant="outline" size="sm" onClick={() => { void queryClient.invalidateQueries({ queryKey: DEEP_DISCOVERY_QUERY_KEY }); onClose() }}>{t('deepDiscovery.reloadConfiguration')}</Button></div> : null}
      <DialogFooter className="justify-end sm:justify-end">
        <Button data-analytics-id="github.discovery.cancel" type="button" variant="outline" onClick={onClose}>{t('deepDiscovery.cancel')}</Button>
        <Button data-analytics-id="github.discovery.save" type="button" disabled={update.isPending || conflict} onClick={save}>{t('deepDiscovery.save')}</Button>
      </DialogFooter>
  </>

  if (embedded) return <div className="space-y-4">{content}</div>
  return <Dialog open onOpenChange={(open) => { if (!open) onClose() }}>
    <DialogContent className="github-sync-surface w-[min(calc(100vw-2rem),42rem)] rounded-md">{content}</DialogContent>
  </Dialog>
}
