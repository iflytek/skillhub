import { ReactNode, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Button } from '@/shared/ui/button'

interface ConfirmDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string | ReactNode
  confirmText?: string
  cancelText?: string
  variant?: 'default' | 'destructive'
  onConfirm: () => void | Promise<void>
  contentTestId?: string
  confirmButtonTestId?: string
  closeOnConfirm?: boolean
}

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmText,
  cancelText,
  variant = 'default',
  onConfirm,
  contentTestId,
  confirmButtonTestId,
  closeOnConfirm = true,
}: ConfirmDialogProps) {
  const { t } = useTranslation()
  const openRef = useRef(open)
  const pendingRef = useRef(false)
  const [pending, setPending] = useState(false)
  openRef.current = open
  const resolvedConfirmText = confirmText ?? t('dialog.confirm')
  const resolvedCancelText = cancelText ?? t('dialog.cancel')

  const handleConfirm = async () => {
    if (pendingRef.current) return
    pendingRef.current = true
    setPending(true)
    try {
      await onConfirm()
      // Skip close if the caller already closed (e.g. publish success + navigate).
      if (closeOnConfirm && openRef.current) onOpenChange(false)
    } finally {
      pendingRef.current = false
      setPending(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => { if (!pendingRef.current) onOpenChange(next) }}>
      <DialogContent data-testid={contentTestId} aria-label={title} hideClose={pending}>
        <DialogHeader className="min-w-0 text-center sm:text-center">
          <DialogTitle className="text-center">{title}</DialogTitle>
          {description && <DialogDescription className="text-center break-all">{description}</DialogDescription>}
        </DialogHeader>
        <DialogFooter className="sm:justify-center sm:space-x-3">
          <Button variant="outline" disabled={pending} onClick={() => onOpenChange(false)}>
            {resolvedCancelText}
          </Button>
          <Button data-testid={confirmButtonTestId} variant={variant} disabled={pending} onClick={handleConfirm}>
            {pending ? t('dialog.processing') : resolvedConfirmText}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
