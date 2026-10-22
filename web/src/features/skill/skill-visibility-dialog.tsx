import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { skillLifecycleApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { toast } from '@/shared/lib/toast'

const scopes = ['PUBLIC', 'NAMESPACE_ONLY', 'PRIVATE'] as const
type Visibility = typeof scopes[number]

export function SkillVisibilityDialog({ namespace, slug, visibility, disabled = false }: {
  namespace: string; slug: string; visibility: string; disabled?: boolean
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [target, setTarget] = useState<Visibility>('PUBLIC')
  const [error, setError] = useState('')
  const state = useQuery({
    queryKey: ['skills', namespace, slug, 'visibility'],
    queryFn: () => skillLifecycleApi.getVisibility(namespace, slug),
    enabled: open,
  })
  const currentVisibility = state.data?.visibility ?? visibility
  const pendingVisibility = state.data?.requestedVisibility
  const mutation = useMutation({
    mutationFn: () => skillLifecycleApi.changeVisibility(namespace, slug, target),
    onSuccess: async (result) => {
      toast.success(t(result.reviewTaskId ? 'skillVisibility.submitted' : 'skillVisibility.saved'))
      setOpen(false)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['skills'] }),
        queryClient.invalidateQueries({ queryKey: ['skill'] }),
        queryClient.invalidateQueries({ queryKey: ['my-skills'] }),
        queryClient.invalidateQueries({ queryKey: ['reviews'] }),
      ])
    },
    onError: (cause) => setError(cause instanceof Error ? cause.message : t('skillVisibility.failed')),
  })
  const expanding = scopes.indexOf(target) < scopes.indexOf(scopes.find(scope => scope === currentVisibility) ?? 'PUBLIC')
  return <>
    <Button data-analytics-id="skill.visibility.open" size="sm" variant="outline" disabled={disabled} onClick={(event) => {
      event.stopPropagation()
      setTarget(scopes.find(scope => scope === visibility) ?? 'PUBLIC')
      setError('')
      setOpen(true)
    }}>{t('skillVisibility.title')}</Button>
    <Dialog open={open} onOpenChange={(value) => { if (!mutation.isPending) setOpen(value) }}>
      <DialogContent closeAnalyticsId="skill.visibility.cancel" onClick={(event) => event.stopPropagation()}>
        <DialogHeader>
          <DialogTitle>{t('skillVisibility.title')}</DialogTitle>
          <DialogDescription>{t('skillVisibility.description')}</DialogDescription>
        </DialogHeader>
        <p className="text-sm text-muted-foreground">{t('skillVisibility.current', { visibility: t(`skillVisibility.${currentVisibility}`) })}</p>
        {pendingVisibility && <p role="status" className="text-sm">{t('skillVisibility.pending', { visibility: t(`skillVisibility.${pendingVisibility}`) })}</p>}
        {state.isError && <p role="alert" className="text-sm text-destructive">{t('skillVisibility.failed')}</p>}
        <Select value={target} onValueChange={(value) => {
          const scope = scopes.find(item => item === value)
          if (scope) setTarget(scope)
        }} disabled={mutation.isPending || Boolean(pendingVisibility)}>
          <SelectTrigger data-analytics-id="skill.visibility.interact" aria-label={t('skillVisibility.title')}><SelectValue /></SelectTrigger>
          <SelectContent>{scopes.map(scope => <SelectItem data-analytics-id={scope === 'PUBLIC' ? 'skill.visibility.public' : scope === 'NAMESPACE_ONLY' ? 'skill.visibility.namespace' : 'skill.visibility.private'} key={scope} value={scope}>{t(`skillVisibility.${scope}`)}</SelectItem>)}</SelectContent>
        </Select>
        <p className="text-sm text-muted-foreground">{t(expanding ? 'skillVisibility.expanding' : 'skillVisibility.narrowing')}</p>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <DialogFooter>
          <Button data-analytics-id="skill.visibility.cancel" variant="outline" disabled={mutation.isPending} onClick={() => setOpen(false)}>{t('dialog.cancel')}</Button>
          <Button data-analytics-id="skill.visibility.save" disabled={mutation.isPending || state.isLoading || state.isError || Boolean(pendingVisibility) || target === currentVisibility} onClick={() => mutation.mutate()}>{t(expanding ? 'skillVisibility.submit' : 'profile.save')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </>
}
