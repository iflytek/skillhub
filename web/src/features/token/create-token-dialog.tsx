import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { tokenApi } from '@/api/client'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { centeredToastOptions, toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import type { CreateTokenRequest, CreateTokenResponse } from '@/api/types'
import { resolveTokenExpiresAt, toLocalDateTimeInputValue, type TokenExpirationMode } from './token-expiration'
import { TOKEN_CAPABILITIES, TOKEN_PRESETS, type TokenPreset } from './token-capabilities'

interface CreateTokenDialogProps {
  children: React.ReactNode
  existingNames?: string[]
}

const MAX_TOKEN_NAME_LENGTH = 64

/**
 * Handles API token creation, including duplicate-name checks, expiration
 * selection, and the one-time reveal of the raw token value after creation.
 */
export function CreateTokenDialog({ children, existingNames = [] }: CreateTokenDialogProps) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [createdToken, setCreatedToken] = useState<CreateTokenResponse | null>(null)
  const [nameError, setNameError] = useState<string | null>(null)
  const [expirationMode, setExpirationMode] = useState<TokenExpirationMode>('never')
  const [customExpiresAt, setCustomExpiresAt] = useState('')
  const [expiresAtError, setExpiresAtError] = useState<string | null>(null)
  const [preset, setPreset] = useState<TokenPreset>('candidate')
  const [customScopes, setCustomScopes] = useState<string[]>([])
  const [, copy] = useCopyToClipboard()
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
      setExpirationMode('never')
      setCustomExpiresAt('')
      setExpiresAtError(null)
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

    const expiresAt = resolveTokenExpiresAt(expirationMode, customExpiresAt)
    if (expirationMode === 'custom' && !expiresAt) {
      setExpiresAtError(t('createToken.expiresAtRequired'))
      return
    }

    setNameError(null)
    setExpiresAtError(null)
    const scopes = preset === 'custom' ? customScopes : TOKEN_PRESETS[preset]
    createMutation.mutate({ name: normalizedName, expiresAt, scopes })
  }

  const handleClose = () => {
    setOpen(false)
    setPreset('candidate')
    setCustomScopes([])
    setCreatedToken(null)
    setName('')
    setNameError(null)
    setExpirationMode('never')
    setCustomExpiresAt('')
    setExpiresAtError(null)
    createMutation.reset()
  }

  const handleCopyToken = async () => {
    if (!createdToken) return

    try {
      await copy(createdToken.token)
      toast.success(t('createToken.copySuccess'), undefined, centeredToastOptions())
    } catch (error) {
      console.error('Failed to copy token:', error)
      toast.error(t('createToken.copyFailed'), undefined, centeredToastOptions())
    }
  }

  const formatExpiresAt = (expiresAt?: string) => {
    if (!expiresAt) {
      return t('token.neverExpires')
    }
    return formatLocalDateTime(expiresAt, i18n.language)
  }

  const minDateTime = toLocalDateTimeInputValue(new Date())

  return (
    // Reopening the dialog resets transient creation state because the raw token
    // is only meant to be shown once, immediately after a successful create call.
    <Dialog open={open} onOpenChange={(nextOpen) => {
      if (nextOpen) {
        setPreset('candidate')
        setCustomScopes([])
        setCreatedToken(null)
        setName('')
        setNameError(null)
        setExpirationMode('never')
        setCustomExpiresAt('')
        setExpiresAtError(null)
        createMutation.reset()
      }
      setOpen(nextOpen)
    }}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent closeAnalyticsId={createdToken ? 'account.token.created.close' : 'account.token.create.cancel'}>
        {!createdToken ? (
          <>
            <DialogHeader className="text-center">
              <DialogTitle>{t('createToken.title')}</DialogTitle>
              <DialogDescription>
                {t('createToken.description')}
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4 py-4">
              <div className="space-y-2">
                <Label htmlFor="token-name">{t('createToken.nameLabel')}</Label>
                <Input data-analytics-id="account.token.create"
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

              <div className="space-y-2">
                <Label htmlFor="token-preset">{t('createToken.presetLabel')}</Label>
                <Select value={preset} onValueChange={(value) => setPreset(value as TokenPreset)}>
                  <SelectTrigger id="token-preset">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {(['readonly', 'candidate', 'private', 'custom'] as const).map((value) => (
                      <SelectItem key={value} value={value}>
                        {t(`createToken.presets.${value}`)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">{t('createToken.capabilityHint')}</p>
                {preset === 'custom' ? (
                  <div className="space-y-2">
                    {TOKEN_CAPABILITIES.map(({ scope, label }) => (
                      <div key={scope} className="flex items-center gap-2">
                        <Input
                          id={`scope-${label}`}
                          type="checkbox"
                          className="h-4 w-4"
                          checked={customScopes.includes(scope)}
                          onChange={(event) => setCustomScopes((current) => event.target.checked
                            ? [...current, scope]
                            : current.filter((item) => item !== scope))}
                        />
                        <Label htmlFor={`scope-${label}`}>
                          {t(`token.capabilityLabels.${label}`)} ({scope})
                        </Label>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="break-words font-mono text-xs">{TOKEN_PRESETS[preset].join(', ')}</p>
                )}
              </div>
              <div className="space-y-2">
                <Label htmlFor="token-expiration">{t('createToken.expirationLabel')}</Label>
                <Select
                  value={expirationMode}
                  onValueChange={(value) => {
                    setExpirationMode(value as TokenExpirationMode)
                    setExpiresAtError(null)
                  }}
                >
                  <SelectTrigger data-analytics-id="account.token.create.expiry.interact" id="token-expiration">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem data-analytics-id="account.token.create.expiry.never" value="never">{t('createToken.expirationNever')}</SelectItem>
                    <SelectItem data-analytics-id="account.token.create.expiry.week" value="7d">{t('createToken.expiration7d')}</SelectItem>
                    <SelectItem data-analytics-id="account.token.create.expiry.month" value="30d">{t('createToken.expiration30d')}</SelectItem>
                    <SelectItem data-analytics-id="account.token.create.expiry.quarter" value="90d">{t('createToken.expiration90d')}</SelectItem>
                    <SelectItem data-analytics-id="account.token.create.expiry.custom" value="custom">{t('createToken.expirationCustom')}</SelectItem>
                  </SelectContent>
                </Select>
                {expirationMode === 'custom' ? (
                  <Input
                    id="token-custom-expiration"
                    type="datetime-local"
                    value={customExpiresAt}
                    min={minDateTime}
                    onChange={(e) => {
                      setCustomExpiresAt(e.target.value)
                      if (expiresAtError) {
                        setExpiresAtError(null)
                      }
                    }}
                  />
                ) : null}
                {expiresAtError ? (
                  <p className="text-xs text-red-600">{expiresAtError}</p>
                ) : (
                  <p className="text-xs text-muted-foreground">{t('createToken.expirationHint')}</p>
                )}
              </div>
            </div>
            {createMutation.error ? (
              <p className="text-sm text-red-600">{createMutation.error.message}</p>
            ) : null}
            <DialogFooter className="sm:justify-center sm:space-x-3">
              <Button data-analytics-id="account.token.create.cancel" variant="outline" onClick={handleClose}>
                {t('dialog.cancel')}
              </Button>
              <Button data-analytics-id="account.token.create"
                onClick={handleCreate}
                disabled={!normalizedName || hasDuplicateName || createMutation.isPending}
              >
                {createMutation.isPending ? t('createToken.creating') : t('createToken.create')}
              </Button>
            </DialogFooter>
          </>
        ) : (
          <>
            <DialogHeader className="min-w-0 text-center">
              <DialogTitle>{t('createToken.successTitle')}</DialogTitle>
              <DialogDescription className="break-words">
                {t('createToken.successDescription')}
              </DialogDescription>
            </DialogHeader>
            <div className="min-w-0 space-y-4 py-4">
              <div className="space-y-2">
                <Label>{t('createToken.tokenLabel')}</Label>
                <div className="overflow-hidden rounded-md bg-muted p-3 font-mono text-sm break-all">
                  {createdToken.token}
                </div>
              </div>
              <div className="space-y-2">
                <Label>{t('createToken.nameDisplay')}</Label>
                <div className="text-sm break-all">{createdToken.name}</div>
              </div>
              <div className="space-y-2">
                <Label>{t('createToken.expiresAtDisplay')}</Label>
                <div className="text-sm">{formatExpiresAt(createdToken.expiresAt)}</div>
              </div>
              <div className="space-y-2">
                <Label>{t('token.capabilities')}</Label>
                <p className="break-words font-mono text-xs">{createdToken.capabilities?.join(', ')}</p>
              </div>
            </div>
            <DialogFooter className="sm:justify-center sm:space-x-3">
              <Button data-analytics-id="account.token.copy" onClick={handleCopyToken}>
                {t('createToken.copyToken')}
              </Button>
              <Button data-analytics-id="account.token.created.close" variant="outline" onClick={handleClose}>
                {t('dialog.close')}
              </Button>
            </DialogFooter>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
