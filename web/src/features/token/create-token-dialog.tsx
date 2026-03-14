import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { tokenApi } from '@/api/client'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { toast } from '@/shared/lib/toast'
import type { CreateTokenRequest, CreateTokenResponse } from '@/api/types'

interface CreateTokenDialogProps {
  children: React.ReactNode
  existingNames?: string[]
}

const MAX_TOKEN_NAME_LENGTH = 64

export function CreateTokenDialog({ children, existingNames = [] }: CreateTokenDialogProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [createdToken, setCreatedToken] = useState<CreateTokenResponse | null>(null)
  const [nameError, setNameError] = useState<string | null>(null)
  const queryClient = useQueryClient()

  const normalizedName = name.trim()
  const hasDuplicateName = existingNames.some(
    (existingName) => existingName.trim().toLocaleLowerCase() === normalizedName.toLocaleLowerCase()
  )

  const createMutation = useMutation({
    mutationFn: (request: CreateTokenRequest) => tokenApi.createToken(request),
    onSuccess: (data) => {
      setCreatedToken(data)
      setName('')
      setNameError(null)
      queryClient.invalidateQueries({ queryKey: ['tokens'] })
    },
  })

  const handleCreate = () => {
    if (!normalizedName) {
      setNameError(t('createToken.nameRequired'))
      return
    }
    if (normalizedName.length > MAX_TOKEN_NAME_LENGTH) {
      setNameError(t('createToken.nameTooLong', { max: MAX_TOKEN_NAME_LENGTH }))
      return
    }
    if (hasDuplicateName) {
      setNameError(t('createToken.nameDuplicate'))
      return
    }

    setNameError(null)
    createMutation.mutate({ name: normalizedName })
  }

  const handleClose = () => {
    setOpen(false)
    setCreatedToken(null)
    setName('')
    setNameError(null)
    createMutation.reset()
  }

  const handleCopyToken = async () => {
    if (!createdToken) return

    try {
      await navigator.clipboard.writeText(createdToken.token)
      toast.success(t('createToken.copySuccess'), undefined, {
        position: 'top-center',
        classNames: {
          title: 'text-center font-semibold',
          description: 'text-center',
        },
      })
    } catch (error) {
      console.error('Failed to copy token:', error)
      toast.error(t('createToken.copyFailed'), undefined, {
        position: 'top-center',
        classNames: {
          title: 'text-center font-semibold',
          description: 'text-center',
        },
      })
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        {!createdToken ? (
          <>
            <DialogHeader>
              <DialogTitle>{t('createToken.title')}</DialogTitle>
              <DialogDescription>
                {t('createToken.description')}
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="token-name">{t('createToken.nameLabel')}</Label>
                <Input
                  id="token-name"
                  placeholder={t('createToken.namePlaceholder')}
                  value={name}
                  maxLength={MAX_TOKEN_NAME_LENGTH}
                  onChange={(e) => {
                    setName(e.target.value)
                    if (nameError) {
                      setNameError(null)
                    }
                  }}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      handleCreate()
                    }
                  }}
                  aria-invalid={nameError || hasDuplicateName ? 'true' : 'false'}
                />
                <div className="flex items-center justify-between gap-3 text-xs">
                  <span className="text-red-600">
                    {nameError ?? (hasDuplicateName && normalizedName ? t('createToken.nameDuplicate') : '')}
                  </span>
                  <span className="text-muted-foreground">
                    {normalizedName.length}/{MAX_TOKEN_NAME_LENGTH}
                  </span>
                </div>
              </div>
            </div>
            {createMutation.error ? (
              <p className="text-sm text-red-600">{createMutation.error.message}</p>
            ) : null}
            <DialogFooter>
              <Button variant="outline" onClick={handleClose}>
                {t('dialog.cancel')}
              </Button>
              <Button
                onClick={handleCreate}
                disabled={!normalizedName || hasDuplicateName || createMutation.isPending}
              >
                {createMutation.isPending ? t('createToken.creating') : t('createToken.create')}
              </Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader>
              <DialogTitle>{t('createToken.successTitle')}</DialogTitle>
              <DialogDescription>
                {t('createToken.successDescription')}
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label>{t('createToken.tokenLabel')}</Label>
                <div className="rounded-md bg-muted p-3 font-mono text-sm break-all">
                  {createdToken.token}
                </div>
              </div>
              <div className="space-y-2">
                <Label>{t('createToken.nameDisplay')}</Label>
                <div className="text-sm">{createdToken.name}</div>
              </div>
            </div>
            <DialogFooter>
              <Button onClick={handleCopyToken}>
                {t('createToken.copyToken')}
              </Button>
              <Button variant="outline" onClick={handleClose}>
                {t('dialog.close')}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
