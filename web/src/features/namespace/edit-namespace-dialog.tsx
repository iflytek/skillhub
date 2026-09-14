import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { Namespace } from '@/api/types'
import { useUpdateNamespace } from '@/shared/hooks/use-namespace-queries'
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
import { Textarea } from '@/shared/ui/textarea'

interface EditNamespaceDialogProps {
  namespace: Namespace
  children: React.ReactNode
}

export function EditNamespaceDialog({ namespace, children }: EditNamespaceDialogProps) {
  const { t } = useTranslation()
  const updateMutation = useUpdateNamespace()
  const [open, setOpen] = useState(false)
  const [displayName, setDisplayName] = useState(namespace.displayName)
  const [description, setDescription] = useState(namespace.description ?? '')
  const [allowMemberOverwrite, setAllowMemberOverwrite] = useState(namespace.allowMemberOverwrite)
  const [displayNameError, setDisplayNameError] = useState<string | null>(null)

  const resetDialog = () => {
    setDisplayName(namespace.displayName)
    setDescription(namespace.description ?? '')
    setAllowMemberOverwrite(namespace.allowMemberOverwrite)
    setDisplayNameError(null)
    updateMutation.reset()
  }

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
    if (!nextOpen) {
      resetDialog()
    }
  }

  const handleSave = async () => {
    const trimmedDisplayName = displayName.trim()
    if (!trimmedDisplayName) {
      setDisplayNameError(t('namespaceEdit.displayNameRequired'))
      return
    }

    try {
      await updateMutation.mutateAsync({
        slug: namespace.slug,
        displayName: trimmedDisplayName,
        description: description.trim(),
        allowMemberOverwrite,
      })
      toast.success(t('namespaceEdit.saveSuccess'))
      setOpen(false)
    } catch (error) {
      toast.error(t('namespaceEdit.saveErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader className="text-center sm:text-center">
          <DialogTitle className="text-center">{t('namespaceEdit.dialogTitle')}</DialogTitle>
          <DialogDescription className="text-center">@{namespace.slug}</DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-2">
          <div className="space-y-2">
            <Label htmlFor="edit-display-name">{t('namespaceEdit.displayNameLabel')}</Label>
            <Input
              id="edit-display-name"
              value={displayName}
              onChange={(event) => {
                setDisplayName(event.target.value)
                if (displayNameError) setDisplayNameError(null)
              }}
              aria-invalid={displayNameError ? 'true' : 'false'}
            />
            {displayNameError ? <p className="text-xs text-red-600">{displayNameError}</p> : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-description">{t('namespaceEdit.descriptionLabel')}</Label>
            <Textarea
              id="edit-description"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              rows={3}
            />
          </div>

          <div className="flex items-start space-x-2">
            <input
              id="edit-allow-member-overwrite"
              type="checkbox"
              checked={allowMemberOverwrite}
              onChange={(event) => setAllowMemberOverwrite(event.target.checked)}
              className="mt-1.5 h-4 w-4 shrink-0 accent-primary"
            />
            <div className="space-y-1">
              <Label htmlFor="edit-allow-member-overwrite">
                {t('namespaceEdit.allowMemberOverwriteLabel')}
              </Label>
              <p className="text-xs text-muted-foreground">
                {t('namespaceEdit.allowMemberOverwriteHint')}
              </p>
            </div>
          </div>
        </div>

        {updateMutation.error ? (
          <p className="text-sm text-red-600">{updateMutation.error.message}</p>
        ) : null}

        <DialogFooter className="sm:justify-center sm:space-x-3">
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            {t('dialog.cancel')}
          </Button>
          <Button type="button" onClick={handleSave} disabled={updateMutation.isPending}>
            {updateMutation.isPending ? t('namespaceEdit.saving') : t('namespaceEdit.saveAction')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
