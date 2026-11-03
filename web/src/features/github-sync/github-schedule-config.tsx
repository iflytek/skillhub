import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { GithubSyncSchedulePolicy, GithubSyncSchedulePolicyInput } from '@/api/client'
import { useUpdateGithubSyncSchedulePolicy } from './use-github-sync-admin'
import { toast } from '@/shared/lib/toast'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'

const fields = ['repositoryHours', 'curatedOwnerHours', 'searchRuleHours'] as const
type Field = typeof fields[number]

export function GithubScheduleConfig({ policy }: { policy: GithubSyncSchedulePolicy }) {
  const { t } = useTranslation()
  const mutation = useUpdateGithubSyncSchedulePolicy()
  const [open, setOpen] = useState(false)
  const [hours, setHours] = useState<Record<Field, string>>({
    repositoryHours: policy.repositoryHours?.toString() ?? '',
    curatedOwnerHours: policy.curatedOwnerHours?.toString() ?? '24',
    searchRuleHours: policy.searchRuleHours?.toString() ?? '72',
  })

  const openEditor = () => {
    setHours({
      repositoryHours: policy.repositoryHours?.toString() ?? '',
      curatedOwnerHours: policy.curatedOwnerHours?.toString() ?? '24',
      searchRuleHours: policy.searchRuleHours?.toString() ?? '72',
    })
    setOpen(true)
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    const body = {
      repositoryHours: Number(hours.repositoryHours),
      curatedOwnerHours: Number(hours.curatedOwnerHours),
      searchRuleHours: Number(hours.searchRuleHours),
    } satisfies GithubSyncSchedulePolicyInput
    if (Object.values(body).some((value) => !Number.isInteger(value) || value < 1 || value > 720)) {
      toast.error(t('githubSync.scheduleInvalid'))
      return
    }
    try {
      await mutation.mutateAsync(body)
      setOpen(false)
      toast.success(t('githubSync.saved'))
    } catch (error) {
      toast.error(t('githubSync.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  return <Card className="sync-section space-y-3 p-4 sm:p-5">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div className="min-w-0 flex-1"><h2 className="font-semibold">{t('githubSync.scheduleTitle')}</h2><p className="text-sm text-muted-foreground">{t('githubSync.scheduleHelp')}</p></div>
      <Button data-analytics-id="github.schedule.edit" size="sm" variant="outline" className="sync-action" onClick={openEditor}>{t('githubSync.edit')}</Button>
    </div>
    <div className="grid gap-3 text-sm sm:grid-cols-3">
      {fields.map((field) => <div key={field}><span className="text-muted-foreground">{t(`githubSync.${field}`)}</span><p className="font-semibold">{policy[field] == null ? '—' : `${policy[field]} ${t('githubSync.hoursUnit')}`}</p></div>)}
    </div>
    {policy.repositoryHours == null ? <p className="text-xs text-muted-foreground">{t('githubSync.scheduleLegacy', { interval: policy.effectiveRepositoryInterval })}</p> : null}
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent closeAnalyticsId="github.schedule.cancel" className="github-sync-surface w-[min(calc(100vw-2rem),38rem)] rounded-md">
        <DialogHeader><DialogTitle>{t('githubSync.scheduleTitle')}</DialogTitle><DialogDescription>{t('githubSync.scheduleHelp')}</DialogDescription></DialogHeader>
        <form data-analytics-id="github.schedule.save" onSubmit={save} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-3">
            {fields.map((field) => <label key={field} className="block text-sm">
              {t(`githubSync.${field}`)}
              <Input type="number" min={1} max={720} step={1} value={hours[field]}
                placeholder={field === 'repositoryHours' ? '24' : undefined}
                onChange={(event) => setHours((current) => ({ ...current, [field]: event.target.value }))} required />
            </label>)}
          </div>
          <div className="flex justify-end gap-2"><Button data-analytics-id="github.schedule.cancel" type="button" size="sm" variant="outline" className="sync-action" onClick={() => setOpen(false)}>{t('githubSync.cancel')}</Button><Button type="submit" size="sm" className="sync-action" disabled={mutation.isPending}>{t('githubSync.save')}</Button></div>
        </form>
      </DialogContent>
    </Dialog>
  </Card>
}
