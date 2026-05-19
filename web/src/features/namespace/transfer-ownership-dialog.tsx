import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Namespace, NamespaceMember } from '@/api/types'
import { useTransferNamespaceOwnership } from '@/shared/hooks/use-namespace-queries'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'

interface TransferOwnershipDialogProps {
  namespace: Namespace
  members: NamespaceMember[]
  children: React.ReactNode
}

export function TransferOwnershipDialog({ namespace, members, children }: TransferOwnershipDialogProps) {
  const { t } = useTranslation()
  const transferMutation = useTransferNamespaceOwnership()
  const [open, setOpen] = useState(false)
  const [selectedUserId, setSelectedUserId] = useState('')
  const [confirmSlug, setConfirmSlug] = useState('')

  const candidates = members.filter((member) => member.role !== 'OWNER')

  const resetDialog = () => {
    setSelectedUserId('')
    setConfirmSlug('')
    transferMutation.reset()
  }

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
    if (!nextOpen) {
      resetDialog()
    }
  }

  const handleTransfer = async () => {
    if (!selectedUserId) return

    try {
      await transferMutation.mutateAsync({
        slug: namespace.slug,
        newOwnerUserId: selectedUserId,
      })
      toast.success(
        t('members.transferSuccessTitle'),
        t('members.transferSuccessDescription', { userId: selectedUserId }),
      )
      setOpen(false)
    } catch (error) {
      toast.error(t('members.transferErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const canConfirm = Boolean(selectedUserId) && confirmSlug === namespace.slug

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader className="text-center sm:text-center">
          <DialogTitle className="text-center">{t('members.transferDialogTitle')}</DialogTitle>
          <DialogDescription className="text-center">
            {t('members.transferDialogDescription')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="rounded-lg border border-amber-500/20 bg-amber-500/10 p-3 text-sm text-amber-700 dark:text-amber-400">
            {t('members.transferWarning')}
          </div>

          <div className="space-y-2">
            <Label htmlFor="transfer-new-owner">{t('members.transferNewOwnerLabel')}</Label>
            {candidates.length > 0 ? (
              <Select value={selectedUserId} onValueChange={setSelectedUserId}>
                <SelectTrigger id="transfer-new-owner">
                  <SelectValue placeholder={t('members.transferSelectPlaceholder')} />
                </SelectTrigger>
                <SelectContent>
                  {candidates.map((candidate) => (
                    <SelectItem key={candidate.userId} value={candidate.userId}>
                      {candidate.displayName || candidate.userId} ({candidate.role})
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            ) : (
              <p className="text-sm text-muted-foreground">{t('members.transferNoCandidates')}</p>
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="transfer-confirm-slug">{t('members.transferConfirmSlugPrompt')}</Label>
            <Input
              id="transfer-confirm-slug"
              value={confirmSlug}
              placeholder={namespace.slug}
              onChange={(event) => setConfirmSlug(event.target.value)}
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
            />
            <p className="text-xs text-muted-foreground">
              {t('members.transferConfirmSlugHint', { slug: namespace.slug })}
            </p>
          </div>
        </div>

        {transferMutation.error ? (
          <p className="text-sm text-red-600">{transferMutation.error.message}</p>
        ) : null}

        <DialogFooter className="sm:justify-center sm:space-x-3">
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            {t('dialog.cancel')}
          </Button>
          <Button
            type="button"
            variant="destructive"
            onClick={handleTransfer}
            disabled={!canConfirm || transferMutation.isPending}
          >
            {transferMutation.isPending ? t('members.transferring') : t('members.transferConfirmAction')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
