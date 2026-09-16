import { useState } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { SkillSuite } from '@/api/types'
import {
  useDeleteSuite,
  useReopenSuiteVersion,
  useSetSuiteArchived,
  useSetSuiteHidden,
  useYankSuiteVersion,
} from '@/shared/hooks/use-suite-queries'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { toast } from '@/shared/lib/toast'

type ConfirmAction = 'hide' | 'restore' | 'archive' | 'unarchive' | 'delete'

export function SuiteManagementActions({ suite }: { suite: SkillSuite }) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [confirmAction, setConfirmAction] = useState<ConfirmAction | null>(null)
  const [yankOpen, setYankOpen] = useState(false)
  const [yankReason, setYankReason] = useState('')
  const reopenMutation = useReopenSuiteVersion()
  const yankMutation = useYankSuiteVersion()
  const hiddenMutation = useSetSuiteHidden()
  const archivedMutation = useSetSuiteArchived()
  const deleteMutation = useDeleteSuite()

  const navigateToEditor = (newVersion: boolean) => {
    if (newVersion) {
      navigate({
        to: `/dashboard/suites/${suite.namespace}/${encodeURIComponent(suite.slug)}/new-version`,
        search: { sourceVersion: suite.version },
      })
      return
    }
    navigate({
      to: `/dashboard/suites/${suite.namespace}/${encodeURIComponent(suite.slug)}/edit`,
      search: { version: suite.version },
    })
  }

  const reopen = async () => {
    try {
      await reopenMutation.mutateAsync({ suiteId: suite.id, versionId: suite.versionId })
      toast.success(t('suite.reopened'))
      navigateToEditor(false)
    } catch (error) {
      toast.error(t('suite.actionFailed'), error instanceof Error ? error.message : '')
    }
  }

  const confirmLifecycleAction = async () => {
    if (!confirmAction) return
    try {
      if (confirmAction === 'hide' || confirmAction === 'restore') {
        await hiddenMutation.mutateAsync({ suiteId: suite.id, hidden: confirmAction === 'hide' })
      } else if (confirmAction === 'archive' || confirmAction === 'unarchive') {
        await archivedMutation.mutateAsync({ suiteId: suite.id, archived: confirmAction === 'archive' })
      } else {
        await deleteMutation.mutateAsync({ suiteId: suite.id })
        toast.success(t('suite.deleted'))
        navigate({ to: '/dashboard/suites' })
        return
      }
      toast.success(t(`suite.${confirmAction}Success`))
    } catch (error) {
      toast.error(t('suite.actionFailed'), error instanceof Error ? error.message : '')
    }
  }

  const yank = async () => {
    if (!yankReason.trim()) return
    try {
      await yankMutation.mutateAsync({
        suiteId: suite.id,
        versionId: suite.versionId,
        reason: yankReason.trim(),
      })
      toast.success(t('suite.yanked'))
      setYankOpen(false)
      setYankReason('')
    } catch (error) {
      toast.error(t('suite.actionFailed'), error instanceof Error ? error.message : '')
    }
  }

  const allowed = new Set(suite.allowedActions)
  const managementActions: SkillSuite['allowedActions'] = [
    'REOPEN',
    'CREATE_VERSION',
    'YANK',
    'HIDE',
    'RESTORE',
    'ARCHIVE',
    'UNARCHIVE',
    'DELETE',
  ]
  if (!managementActions.some((action) => allowed.has(action))) return null

  const confirmTitle = confirmAction ? t(`suite.${confirmAction}ConfirmTitle`) : ''
  const confirmDescription = confirmAction
    ? t(`suite.${confirmAction}ConfirmDescription`, { name: suite.displayName })
    : ''

  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold">{t('suite.managementTitle')}</h2>
      <p className="mt-1 text-sm text-muted-foreground">{t('suite.managementDescription')}</p>
      <div className="mt-4 flex flex-wrap gap-2">
        {allowed.has('REOPEN') ? (
          <Button variant="outline" disabled={reopenMutation.isPending} onClick={reopen}>
            {t('suite.reopenDraft')}
          </Button>
        ) : null}
        {allowed.has('CREATE_VERSION') ? (
          <Button variant="outline" onClick={() => navigateToEditor(true)}>{t('suite.createVersion')}</Button>
        ) : null}
        {allowed.has('YANK') ? (
          <Button variant="destructive" onClick={() => setYankOpen(true)}>{t('suite.yankVersion')}</Button>
        ) : null}
        {allowed.has('HIDE') || allowed.has('RESTORE') ? (
          <Button variant="outline" onClick={() => setConfirmAction(suite.hidden ? 'restore' : 'hide')}>
            {t(suite.hidden ? 'suite.restore' : 'suite.hide')}
          </Button>
        ) : null}
        {allowed.has('ARCHIVE') || allowed.has('UNARCHIVE') ? (
          <Button
            variant="outline"
            onClick={() => setConfirmAction(suite.suiteStatus === 'ARCHIVED' ? 'unarchive' : 'archive')}
          >
            {t(suite.suiteStatus === 'ARCHIVED' ? 'suite.unarchive' : 'suite.archive')}
          </Button>
        ) : null}
        {allowed.has('DELETE') ? (
          <Button
            variant="destructive"
            onClick={() => setConfirmAction('delete')}
          >
            {t('suite.delete')}
          </Button>
        ) : null}
      </div>

      <ConfirmDialog
        open={confirmAction !== null}
        onOpenChange={(open) => { if (!open) setConfirmAction(null) }}
        title={confirmTitle}
        description={confirmDescription}
        confirmText={confirmAction ? t(`suite.${confirmAction}`) : undefined}
        variant={confirmAction === 'delete' || confirmAction === 'archive' ? 'destructive' : 'default'}
        onConfirm={confirmLifecycleAction}
      />

      <Dialog open={yankOpen} onOpenChange={setYankOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('suite.yankConfirmTitle')}</DialogTitle>
            <DialogDescription>{t('suite.yankConfirmDescription', { version: suite.version })}</DialogDescription>
          </DialogHeader>
          <Input
            value={yankReason}
            onChange={(event) => setYankReason(event.target.value)}
            placeholder={t('suite.yankReasonPlaceholder')}
          />
          <DialogFooter>
            <Button variant="outline" onClick={() => setYankOpen(false)}>{t('suite.cancel')}</Button>
            <Button
              variant="destructive"
              disabled={!yankReason.trim() || yankMutation.isPending}
              onClick={yank}
            >{t('suite.yankVersion')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </Card>
  )
}
