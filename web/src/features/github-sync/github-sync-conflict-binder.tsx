import { useState, type FormEvent } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { githubSyncAdminApi, githubTeamSourcesApi, type GithubSyncSkillPath } from '@/api/client'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'

export function GithubSyncConflictBinder({ path, namespaceSlug }: {
  path: GithubSyncSkillPath
  namespaceSlug?: string
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [skillId, setSkillId] = useState('')
  const [reason, setReason] = useState('')
  const ingestionId = path.ingestionId
  const pathMove = path.ingestionErrorCode === 'PATH_MOVE_AMBIGUOUS'
  const bind = useMutation({
    mutationFn: () => namespaceSlug
      ? githubTeamSourcesApi.bindExistingSkill(namespaceSlug, ingestionId!, Number(skillId), reason.trim())
      : githubSyncAdminApi.bindExistingSkill(ingestionId!, Number(skillId), reason.trim()),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['github-sync'] }),
  })

  if (!ingestionId || !pathMove && (path.ingestionErrorCode !== 'SLUG_CONFLICT' || !path.targetSkillSlug)) {
    return null
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!Number.isSafeInteger(Number(skillId)) || Number(skillId) < 1 || reason.trim().length < 10) return
    try {
      await bind.mutateAsync()
      setOpen(false)
      setSkillId('')
      setReason('')
      toast.success(t('githubSync.bindingQueued'))
    } catch (error) {
      toast.error(t('githubSync.bindingFailed'), error instanceof Error ? error.message : '')
    }
  }

  return (
    <>
      <Button data-analytics-id={pathMove ? 'github.binding.pathmove.open' : 'github.binding.conflict.open'} size="sm" className="sync-action" variant="outline" onClick={() => setOpen(true)}>
        {t(pathMove ? 'githubSync.resolvePathMove' : 'githubSync.resolveConflict')}
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent closeAnalyticsId={pathMove ? 'github.binding.pathmove.cancel' : 'github.binding.conflict.cancel'} className="github-sync-surface rounded-md">
          <DialogHeader>
            <DialogTitle>{t(pathMove ? 'githubSync.confirmPathMoveTitle' : 'githubSync.bindExistingTitle')}</DialogTitle>
            <DialogDescription>{pathMove
              ? t('githubSync.confirmPathMoveHelp', { path: path.skillPath })
              : t('githubSync.bindExistingHelp', { slug: path.targetSkillSlug, path: path.skillPath })}</DialogDescription>
          </DialogHeader>
          <form data-analytics-id={pathMove ? 'github.binding.pathmove.confirm' : 'github.binding.conflict.confirm'} onSubmit={submit} className="space-y-4">
            <label className="block space-y-1 text-sm">{t('githubSync.targetSkillId')}
              <Input type="number" min="1" step="1" required value={skillId} onChange={(event) => setSkillId(event.target.value)} />
            </label>
            <label className="block space-y-1 text-sm">{t('githubSync.bindingReason')}
              <Textarea required minLength={10} maxLength={1000} value={reason} onChange={(event) => setReason(event.target.value)} />
            </label>
            <DialogFooter>
              <Button data-analytics-id={pathMove ? 'github.binding.pathmove.cancel' : 'github.binding.conflict.cancel'} type="button" size="sm" className="sync-action" variant="outline" onClick={() => setOpen(false)}>{t('githubSync.cancel')}</Button>
              <Button type="submit" size="sm" className="sync-action" disabled={bind.isPending || !Number.isSafeInteger(Number(skillId)) || Number(skillId) < 1 || reason.trim().length < 10}>{t('githubSync.confirmBinding')}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}
