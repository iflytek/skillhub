import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAddCollectionContributor } from '@/shared/hooks/use-collection-queries'
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

interface AddCollectionContributorDialogProps {
  collectionId: string
  children: React.ReactNode
}

export function AddCollectionContributorDialog({ collectionId, children }: AddCollectionContributorDialogProps) {
  const { t } = useTranslation()
  const addContributorMutation = useAddCollectionContributor()
  const [open, setOpen] = useState(false)
  const [userId, setUserId] = useState('')
  const [userIdError, setUserIdError] = useState<string | null>(null)

  const closeDialog = () => {
    setOpen(false)
    setUserId('')
    setUserIdError(null)
    addContributorMutation.reset()
  }

  const handleSubmit = async () => {
    const normalizedUserId = userId.trim()
    if (!normalizedUserId) {
      setUserIdError(t('collections.contributorsUserIdRequired'))
      return
    }
    try {
      await addContributorMutation.mutateAsync({
        id: collectionId,
        userId: normalizedUserId,
      })
      toast.success(t('collections.contributorsAddSuccess'))
      closeDialog()
    } catch (error) {
      toast.error(t('collections.contributorsAddError'), error instanceof Error ? error.message : '')
    }
  }

  return (
    <Dialog open={open} onOpenChange={(nextOpen) => (nextOpen ? setOpen(true) : closeDialog())}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent data-testid="collection-detail-contributor-dialog">
        <DialogHeader className="text-center sm:text-center">
          <DialogTitle>{t('collections.contributorsAddTitle')}</DialogTitle>
          <DialogDescription>{t('collections.contributorsAddDescription')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-2 py-2">
          <Label htmlFor="collection-contributor-user-id">{t('collections.contributorsUserIdLabel')}</Label>
          <Input
            id="collection-contributor-user-id"
            value={userId}
            onChange={(event) => {
              setUserId(event.target.value)
              if (userIdError) {
                setUserIdError(null)
              }
            }}
            autoCapitalize="none"
            autoCorrect="off"
            spellCheck={false}
            aria-invalid={userIdError ? 'true' : 'false'}
          />
          <p className={`text-xs ${userIdError ? 'text-destructive' : 'text-muted-foreground'}`}>
            {userIdError ?? t('collections.contributorsUserIdHint')}
          </p>
        </div>
        <DialogFooter className="sm:justify-center sm:space-x-3">
          <Button type="button" variant="outline" onClick={closeDialog}>
            {t('dialog.cancel')}
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={addContributorMutation.isPending}
            data-testid="collection-detail-add-contributor-submit"
          >
            {addContributorMutation.isPending ? t('collections.contributorsAdding') : t('collections.contributorsAdd')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
