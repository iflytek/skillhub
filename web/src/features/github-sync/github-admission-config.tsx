import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { GithubSyncAdmissionPolicy, GithubSyncAdmissionPolicyInput } from '@/api/client'
import { useUpdateGithubSyncAdmissionPolicy } from './use-github-sync-admin'
import { toast } from '@/shared/lib/toast'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'

type Field = keyof GithubSyncAdmissionPolicyInput
const fields: Field[] = [
  'globalHigh', 'globalLow', 'namespaceHigh', 'namespaceLow',
  'candidatePendingLimit', 'candidatePerLeaseLimit',
]

export function GithubAdmissionConfig({ policy }: { policy: GithubSyncAdmissionPolicy }) {
  const { t } = useTranslation()
  const mutation = useUpdateGithubSyncAdmissionPolicy()
  const [open, setOpen] = useState(false)
  const [values, setValues] = useState<Record<Field, string>>(() => Object.fromEntries(
    fields.map((field) => [field, policy[field]?.toString() ?? '']),
  ) as Record<Field, string>)

  const openEditor = () => {
    setValues(Object.fromEntries(fields.map((field) => [field, policy[field]?.toString() ?? ''])) as Record<Field, string>)
    setOpen(true)
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    const body = Object.fromEntries(fields.map((field) => [field, Number(values[field])])) as GithubSyncAdmissionPolicyInput
    if (fields.some((field) => !Number.isInteger(body[field]))
        || body.globalLow < 0 || body.globalLow >= body.globalHigh
        || body.namespaceLow < 0 || body.namespaceLow >= body.namespaceHigh
        || body.candidatePerLeaseLimit < 1 || body.candidatePerLeaseLimit > body.candidatePendingLimit) {
      toast.error(t('githubSync.invalidWatermarks'))
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
      <div className="min-w-0 flex-1"><h2 className="font-semibold">{t('githubSync.capacityTitle')}</h2><p className="text-sm text-muted-foreground">{t('githubSync.capacityHint')}</p></div>
      <Button data-analytics-id="github.admission.edit" size="sm" variant="outline" className="sync-action" onClick={openEditor}>{t('githubSync.edit')}</Button>
    </div>
    <div className="grid gap-3 text-sm sm:grid-cols-2 lg:grid-cols-4">
      <div><span className="text-muted-foreground">{t('githubSync.platformWatermark')}</span><p className="font-semibold">{policy.globalLow ?? '—'} / {policy.globalHigh ?? '—'}</p></div>
      <div><span className="text-muted-foreground">{t('githubSync.namespaceWatermark')}</span><p className="font-semibold">{policy.namespaceLow ?? '—'} / {policy.namespaceHigh ?? '—'}</p></div>
      <div><span className="text-muted-foreground">{t('githubSync.globalUnresolved')}</span><p className="font-semibold">{policy.globalUnresolvedCount ?? '—'}</p></div>
      <div><span className="text-muted-foreground">{t('githubSync.platformPendingCandidates')}</span><p className="font-semibold">{policy.platformPendingCandidateCount ?? '—'} / {policy.candidatePendingLimit}</p></div>
    </div>
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogContent closeAnalyticsId="github.admission.cancel" className="github-sync-surface w-[min(calc(100vw-2rem),42rem)] rounded-md">
        <DialogHeader><DialogTitle>{t('githubSync.capacityTitle')}</DialogTitle><DialogDescription>{t('githubSync.capacityConfigHelp')}</DialogDescription></DialogHeader>
        <form data-analytics-id="github.admission.save" onSubmit={save} className="space-y-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            {fields.map((field) => <label key={field} className="block text-sm">{t(`githubSync.${field}`)}
              <Input type="number" min={field.endsWith('Low') ? 0 : 1} step={1} value={values[field]}
                onChange={(event) => setValues((current) => ({ ...current, [field]: event.target.value }))} required />
            </label>)}
          </div>
          <div className="flex justify-end gap-2"><Button data-analytics-id="github.admission.cancel" type="button" size="sm" variant="outline" className="sync-action" onClick={() => setOpen(false)}>{t('githubSync.cancel')}</Button><Button type="submit" size="sm" className="sync-action" disabled={mutation.isPending}>{t('githubSync.save')}</Button></div>
        </form>
      </DialogContent>
    </Dialog>
  </Card>
}
