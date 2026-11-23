import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  AlertCircle,
  Check,
  CheckCircle2,
  Copy,
  KeyRound,
  LockKeyhole,
} from 'lucide-react'
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
import { DateTimePicker } from '@/shared/ui/date-time-picker'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { centeredToastOptions, toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import type { CreateTokenRequest, CreateTokenResponse } from '@/api/types'
import {
  resolveTokenExpiresAt,
  type TokenExpirationMode,
} from './token-expiration'

interface CreateTokenDialogProps {
  children: React.ReactNode
  existingNames?: string[]
}
const MAX_TOKEN_NAME_LENGTH = 64

export function CreateTokenDialog({
  children,
  existingNames = [],
}: CreateTokenDialogProps) {
  const { t, i18n } = useTranslation()
  const [open, setOpen] = useState(false)
  const [name, setName] = useState('')
  const [createdToken, setCreatedToken] = useState<CreateTokenResponse | null>(
    null,
  )
  const [nameError, setNameError] = useState<string | null>(null)
  const [expirationMode, setExpirationMode] =
    useState<TokenExpirationMode>('never')
  const [customExpiresAt, setCustomExpiresAt] = useState('')
  const [expiresAtError, setExpiresAtError] = useState<string | null>(null)
  const [copied, setCopied] = useState(false)
  const pendingRef = useRef(false)
  const successTitleRef = useRef<HTMLHeadingElement>(null)
  const [, copy] = useCopyToClipboard()
  const queryClient = useQueryClient()
  const normalizedName = name.trim()
  const hasDuplicateName = existingNames.some(
    (value) =>
      value.trim().toLocaleLowerCase() === normalizedName.toLocaleLowerCase(),
  )

  const createMutation = useMutation({
    gcTime: 0,
    mutationFn: (request: CreateTokenRequest) => tokenApi.createToken(request),
    onSuccess: (data) => {
      setCreatedToken(data)
      queryClient.invalidateQueries({ queryKey: ['tokens'] })
    },
    onSettled: () => {
      pendingRef.current = false
    },
  })

  useEffect(() => {
    if (createdToken) successTitleRef.current?.focus()
  }, [createdToken])

  const reset = () => {
    setCreatedToken(null)
    setCopied(false)
    setName('')
    setNameError(null)
    setExpirationMode('never')
    setCustomExpiresAt('')
    setExpiresAtError(null)
    createMutation.reset()
  }
  const handleOpenChange = (next: boolean) => {
    if (pendingRef.current) return
    reset()
    setOpen(next)
  }
  const handleCreate = (event: React.FormEvent) => {
    event.preventDefault()
    if (pendingRef.current) return
    if (
      !normalizedName ||
      normalizedName.length > MAX_TOKEN_NAME_LENGTH ||
      hasDuplicateName
    ) {
      setNameError(
        t(
          !normalizedName
            ? 'createToken.nameRequired'
            : hasDuplicateName
              ? 'createToken.nameDuplicate'
              : 'createToken.nameTooLong',
          { max: MAX_TOKEN_NAME_LENGTH },
        ),
      )
      return
    }
    if (
      expirationMode === 'custom' &&
      (!customExpiresAt ||
        !Number.isFinite(new Date(customExpiresAt).getTime()) ||
        new Date(customExpiresAt).getTime() <= Date.now())
    ) {
      setExpiresAtError(t('createToken.expiresAtFuture'))
      return
    }
    const expiresAt = resolveTokenExpiresAt(expirationMode, customExpiresAt)
    setNameError(null)
    setExpiresAtError(null)
    pendingRef.current = true
    createMutation.mutate({ name: normalizedName, expiresAt })
  }
  const handleCopyToken = async () => {
    if (!createdToken) return
    try {
      await copy(createdToken.token)
      setCopied(true)
      toast.success(
        t('createToken.copySuccess'),
        undefined,
        centeredToastOptions(),
      )
    } catch {
      toast.error(
        t('createToken.copyFailed'),
        undefined,
        centeredToastOptions(),
      )
    }
  }
  const busy = createMutation.isPending
  const duplicateMessage =
    nameError ??
    (hasDuplicateName && normalizedName ? t('createToken.nameDuplicate') : null)

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent
        className={cn(
          'flex max-h-[min(90dvh,56rem)] flex-col gap-0 overflow-hidden rounded-xl p-0',
          createdToken
            ? 'w-[min(calc(100vw-2rem),42.5rem)]'
            : 'w-[min(calc(100vw-2rem),42.5rem)]',
        )}
        hideClose={busy}
        closeAnalyticsId={
          createdToken
            ? 'account.token.created.close'
            : 'account.token.create.cancel'
        }
      >
        <DialogHeader className="shrink-0 border-b px-5 py-4 pr-12 text-left sm:px-7">
          <div className="flex items-start gap-3">
            <span
              className={cn(
                'flex h-10 w-10 shrink-0 items-center justify-center rounded-xl',
                createdToken
                  ? 'bg-success/10 text-success'
                  : 'bg-info/10 text-info',
              )}
            >
              {createdToken ? (
                <CheckCircle2 className="h-5 w-5" aria-hidden="true" />
              ) : (
                <KeyRound className="h-5 w-5" aria-hidden="true" />
              )}
            </span>
            <div className="min-w-0 space-y-1">
              <DialogTitle
                ref={successTitleRef}
                tabIndex={createdToken ? -1 : undefined}
                className="text-xl outline-none"
              >
                {t(
                  createdToken
                    ? 'createToken.successTitle'
                    : 'createToken.title',
                )}
              </DialogTitle>
              <DialogDescription className="text-xs leading-5">
                {createdToken
                  ? createdToken.name
                  : t('createToken.description')}
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>
        {createdToken ? (
          <>
            <div className="min-h-0 space-y-5 overflow-y-auto px-5 py-6 sm:px-7">
              <div className="flex gap-3 rounded-lg bg-warning/10 p-4 text-sm">
                <LockKeyhole
                  className="mt-0.5 h-5 w-5 shrink-0 text-warning"
                  aria-hidden="true"
                />
                <div>
                  <p className="font-medium">{t('createToken.saveNow')}</p>
                  <p className="mt-1 text-xs leading-5 text-muted-foreground">
                    {t('createToken.successDescription')}
                  </p>
                </div>
              </div>
              <div className="space-y-2">
                <Label>{t('createToken.tokenLabel')}</Label>
                <div className="rounded-lg border bg-muted/40 p-3">
                  <div className="mb-2 flex justify-end">
                    <Button
                      data-analytics-id="account.token.copy"
                      size="sm"
                      variant="outline"
                      onClick={handleCopyToken}
                    >
                      {copied ? (
                        <Check className="h-4 w-4" aria-hidden="true" />
                      ) : (
                        <Copy className="h-4 w-4" aria-hidden="true" />
                      )}
                      {t(
                        copied ? 'createToken.copied' : 'createToken.copyToken',
                      )}
                    </Button>
                  </div>
                  <code className="block select-text break-all font-mono text-sm">
                    {createdToken.token}
                  </code>
                </div>
                <p className="text-xs text-muted-foreground">
                  {t('createToken.manualCopyHint')}
                </p>
              </div>
              <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-5 gap-y-3 text-sm">
                <dt className="text-muted-foreground">
                  {t('createToken.expiresAtDisplay')}
                </dt>
                <dd>
                  {createdToken.expiresAt
                    ? formatLocalDateTime(createdToken.expiresAt, i18n.language)
                    : t('token.neverExpires')}
                </dd>
                <dt className="text-muted-foreground">
                  {t('token.capabilities')}
                </dt>
                <dd className="break-words font-mono text-xs leading-5">
                  {createdToken.capabilities?.join(', ') ??
                    t('token.capabilitiesUnavailable')}
                </dd>
              </dl>
            </div>
            <DialogFooter className="shrink-0 border-t px-5 py-4 sm:justify-end sm:px-7">
              <Button
                data-analytics-id="account.token.created.close"
                onClick={() => handleOpenChange(false)}
              >
                {t('createToken.savedClose')}
              </Button>
            </DialogFooter>
          </>
        ) : (
          <form
            onSubmit={handleCreate}
            className="flex min-h-0 flex-1 flex-col"
          >
            <div className="min-h-0 overflow-y-auto px-5 py-4 sm:px-7">
              <div className="min-w-0 space-y-4">
                <div className="space-y-2">
                  <div className="flex items-center">
                    <Label htmlFor="token-name">
                      {t('createToken.nameLabel')}
                    </Label>
                    <span
                      aria-hidden="true"
                      className="ml-2 rounded-full bg-muted px-2 py-0.5 text-xs font-normal text-muted-foreground"
                    >
                      {t('createToken.required')}
                    </span>
                  </div>
                  <Input
                    id="token-name"
                    data-analytics-id="account.token.create"
                    value={name}
                    placeholder={t('createToken.namePlaceholder')}
                    disabled={busy}
                    maxLength={MAX_TOKEN_NAME_LENGTH}
                    required
                    aria-invalid={!!duplicateMessage}
                    aria-describedby={
                      duplicateMessage
                        ? 'token-name-help token-name-error'
                        : 'token-name-help'
                    }
                    onChange={(e) => {
                      setName(e.target.value)
                      setNameError(null)
                    }}
                  />
                  <div className="flex items-start justify-between gap-3 text-xs leading-4">
                    <p id="token-name-help" className="text-muted-foreground">
                      {t('createToken.nameHint')}
                    </p>
                    <span className="shrink-0 tabular-nums text-muted-foreground">
                      {normalizedName.length}/{MAX_TOKEN_NAME_LENGTH}
                    </span>
                  </div>
                  {duplicateMessage && (
                    <p
                      id="token-name-error"
                      role="alert"
                      className="text-xs text-destructive"
                    >
                      {duplicateMessage}
                    </p>
                  )}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="token-expiration">
                    {t('createToken.expirationLabel')}
                  </Label>
                  <div className="grid items-center gap-2 sm:grid-cols-[12.5rem_minmax(0,1fr)]">
                    <Select
                      value={expirationMode}
                      disabled={busy}
                      onValueChange={(value) => {
                        setExpirationMode(value as TokenExpirationMode)
                        setExpiresAtError(null)
                      }}
                    >
                      <SelectTrigger
                        id="token-expiration"
                        data-analytics-id="account.token.create.expiry.interact"
                        className="h-9 w-full text-xs sm:h-7 sm:w-[12.5rem]"
                        aria-describedby="token-expiration-help"
                      >
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent density="compact">
                        {(['never', '7d', '30d', '90d', 'custom'] as const).map(
                          (value) => (
                            <SelectItem
                              key={value}
                              data-analytics-id={value === 'never' ? 'account.token.create.expiry.never'
                                : value === '7d' ? 'account.token.create.expiry.week'
                                  : value === '30d' ? 'account.token.create.expiry.month'
                                    : value === '90d' ? 'account.token.create.expiry.quarter'
                                      : 'account.token.create.expiry.custom'}
                              value={value}
                            >
                              {t(
                                `createToken.${{ never: 'expirationNever', '7d': 'expiration7d', '30d': 'expiration30d', '90d': 'expiration90d', custom: 'expirationCustom' }[value]}`,
                              )}
                            </SelectItem>
                          ),
                        )}
                      </SelectContent>
                    </Select>
                    {expirationMode === 'custom' && (
                      <DateTimePicker
                        id="token-custom-expiration"
                        aria-label={t('createToken.expirationCustom')}
                        disabled={busy}
                        value={customExpiresAt}
                        min={new Date()}
                        aria-invalid={!!expiresAtError}
                        aria-describedby="token-expiration-help"
                        onChange={(value) => {
                          setCustomExpiresAt(value)
                          setExpiresAtError(null)
                        }}
                      />
                    )}
                    <p
                      id="token-expiration-help"
                      role={expiresAtError ? 'alert' : undefined}
                      className={cn(
                        'text-xs leading-5',
                        expirationMode === 'custom'
                          ? 'sm:col-span-2'
                          : 'hidden sm:block',
                        expiresAtError
                          ? 'text-destructive'
                          : 'text-muted-foreground',
                      )}
                    >
                      {expiresAtError ?? t('createToken.expirationHint')}
                    </p>
                  </div>
                </div>
              </div>
              {createMutation.error && (
                <p
                  role="alert"
                  className="mt-4 flex items-start gap-2 rounded-lg bg-destructive/5 p-3 text-sm text-destructive md:col-span-2"
                >
                  <AlertCircle
                    className="mt-0.5 h-4 w-4 shrink-0"
                    aria-hidden="true"
                  />
                  {createMutation.error.message}
                </p>
              )}
            </div>
            <DialogFooter className="shrink-0 flex-row justify-between border-t px-5 py-4 sm:px-7">
              <p className="hidden items-center gap-2 text-xs text-muted-foreground sm:flex">
                <LockKeyhole className="h-4 w-4 shrink-0" aria-hidden="true" />
                {t('createToken.oneTimeHint')}
              </p>
              <div className="flex w-full gap-3 sm:w-auto">
                <Button
                  data-analytics-id="account.token.create.cancel"
                  type="button"
                  variant="outline"
                  disabled={busy}
                  onClick={() => handleOpenChange(false)}
                  className="flex-1 sm:flex-none"
                >
                  {t('dialog.cancel')}
                </Button>
                <Button
                  data-analytics-id="account.token.create"
                  type="submit"
                  disabled={
                    !normalizedName ||
                    hasDuplicateName ||
                    busy
                  }
                  className="flex-1 sm:flex-none"
                >
                  {t(busy ? 'createToken.creating' : 'createToken.create')}
                </Button>
              </div>
            </DialogFooter>
          </form>
        )}
      </DialogContent>
    </Dialog>
  )
}
