import { useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
  AlertCircle,
  BookOpen,
  Download,
  KeyRound,
  Plus,
  Search,
  ShieldCheck,
  Terminal,
  Trash2,
  Upload,
} from 'lucide-react'
import { tokenApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { CreateTokenDialog } from './create-token-dialog'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Pagination } from '@/shared/components/pagination'
import { centeredToastOptions, toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { Input } from '@/shared/ui/input'
import { DateTimePicker } from '@/shared/ui/date-time-picker'
import type { ApiToken } from '@/api/types'
import {
  resolveTokenExpiresAt,
  toLocalDateTimeInputValue,
  type TokenExpirationMode,
} from './token-expiration'
import {
  TOKEN_TABLE_ACTIONS_HEAD_CLASS_NAME,
  TOKEN_TABLE_HEAD_CLASS_NAME,
} from './token-table-style'
import { getTokenPreset, TOKEN_CAPABILITIES } from './token-capabilities'
import { cn } from '@/shared/lib/utils'
import { TokenPermissionDetails } from './token-permission-details'

const PAGE_SIZE = 10
type TokenPage = {
  items: ApiToken[]
  total: number
  page: number
  size: number
}

/**
 * Dashboard token-management panel.
 *
 * It owns token pagination, optimistic deletion, expiration editing, and creation entry points for
 * personal API credentials.
 */
export function TokenList() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [searchInput, setSearchInput] = useState('')
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState<'ALL' | 'ACTIVE' | 'EXPIRED'>('ALL')
  const [helpOpen, setHelpOpen] = useState(false)
  const lastPageRef = useRef<{
    query: string
    status: string
    data: TokenPage
  } | null>(null)
  const [deleteDialog, setDeleteDialog] = useState<{
    open: boolean
    tokenId?: number
    name?: string
  }>({
    open: false,
  })
  const [expirationDialog, setExpirationDialog] = useState<{
    open: boolean
    tokenId?: number
    tokenName?: string
    mode: TokenExpirationMode
    customExpiresAt: string
  }>({
    open: false,
    mode: 'never',
    customExpiresAt: '',
  })

  const {
    data: responsePage,
    isLoading,
    isFetching,
    isError,
    error,
    refetch,
  } = useQuery<TokenPage>({
    queryKey: ['tokens', page, PAGE_SIZE, query, status],
    queryFn: () => tokenApi.getTokens({ page, size: PAGE_SIZE, query, status }),
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[3] === query &&
      previousQuery.queryKey[4] === status
        ? previous
        : undefined,
    meta: {
      skipGlobalErrorHandler: true,
    },
  })

  if (responsePage) lastPageRef.current = { query, status, data: responsePage }
  const tokenPage =
    responsePage ??
    (lastPageRef.current?.query === query &&
    lastPageRef.current.status === status
      ? lastPageRef.current.data
      : undefined)

  const updateExpirationMutation = useMutation({
    mutationFn: ({
      tokenId,
      expiresAt,
    }: {
      tokenId: number
      expiresAt?: string
    }) => tokenApi.updateTokenExpiration(tokenId, expiresAt),
    onSuccess: () => {
      setExpirationDialog({ open: false, mode: 'never', customExpiresAt: '' })
      queryClient.invalidateQueries({ queryKey: ['tokens'] })
      toast.success(t('token.updateExpirationSuccess'))
    },
    onError: () => {
      toast.error(t('token.updateExpirationFailed'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (tokenId: number) => tokenApi.deleteToken(tokenId),
    onMutate: async (tokenId) => {
      // Remove the token optimistically so the table feels responsive while the server processes the
      // revocation request.
      await queryClient.cancelQueries({ queryKey: ['tokens'] })

      const previousPages = queryClient.getQueriesData<TokenPage>({
        queryKey: ['tokens'],
      })
      queryClient.setQueriesData<TokenPage>(
        { queryKey: ['tokens'] },
        (current) => {
          if (!current) {
            return current
          }

          const nextItems = current.items.filter(
            (token) => token.id !== tokenId,
          )
          if (nextItems.length === current.items.length) {
            return current
          }

          return {
            ...current,
            items: nextItems,
            total: Math.max(current.total - 1, 0),
          }
        },
      )

      return { previousPages, wasLastItemOnPage: tokenPage?.items.length === 1 }
    },
    onSuccess: (_data, _tokenId, context) => {
      if (context?.wasLastItemOnPage && page > 0) {
        setPage(page - 1)
      }
      setDeleteDialog({ open: false })
      queryClient.invalidateQueries({ queryKey: ['tokens'] })
      toast.success(t('token.deleteSuccess'), undefined, centeredToastOptions())
    },
    onError: (_error, _tokenId, context) => {
      context?.previousPages.forEach(([queryKey, previousPage]) => {
        queryClient.setQueryData(queryKey, previousPage)
      })
      toast.error(t('token.deleteFailed'), undefined, centeredToastOptions())
    },
  })

  const handleDelete = (tokenId: number, name: string) => {
    setDeleteDialog({ open: true, tokenId, name })
  }

  const handleEditExpiration = (token: ApiToken) => {
    setExpirationDialog({
      open: true,
      tokenId: token.id,
      tokenName: token.name,
      mode: token.expiresAt ? 'custom' : 'never',
      customExpiresAt: token.expiresAt
        ? toLocalDateTimeInputValue(token.expiresAt)
        : '',
    })
  }

  const confirmDelete = async () => {
    if (deleteDialog.tokenId) {
      await deleteMutation
        .mutateAsync(deleteDialog.tokenId)
        .catch(() => undefined)
    }
  }

  const confirmExpirationUpdate = async () => {
    if (!expirationDialog.tokenId) {
      return
    }

    if (expirationDialog.mode === 'custom' && (
      !expirationDialog.customExpiresAt ||
      !Number.isFinite(new Date(expirationDialog.customExpiresAt).getTime()) ||
      new Date(expirationDialog.customExpiresAt).getTime() <= Date.now()
    )) {
      toast.error(t(expirationDialog.customExpiresAt
        ? 'createToken.expiresAtFuture'
        : 'createToken.expiresAtRequired'))
      return
    }

    const expiresAt = resolveTokenExpiresAt(
      expirationDialog.mode,
      expirationDialog.customExpiresAt,
    )
    if (expirationDialog.mode === 'custom' && !expiresAt) {
      toast.error(t('createToken.expiresAtRequired'))
      return
    }

    await updateExpirationMutation
      .mutateAsync({
        tokenId: expirationDialog.tokenId,
        expiresAt,
      })
      .catch(() => undefined)
  }

  const formatDate = (dateString?: string | null, emptyLabel = '-') => {
    if (!dateString) return emptyLabel
    return formatLocalDateTime(dateString, i18n.language)
  }

  const tokens = tokenPage?.items ?? []
  const totalPages = tokenPage
    ? Math.max(Math.ceil(tokenPage.total / tokenPage.size), 1)
    : 1

  const displayPage = tokenPage?.page ?? page
  const clearFilters = () => {
    setSearchInput('')
    setQuery('')
    setStatus('ALL')
    setPage(0)
  }

  return (
    <div className="space-y-6">
      <div className="flex items-start justify-between gap-4 rounded-lg bg-info/5 p-4">
        <ShieldCheck
          className="mt-0.5 h-5 w-5 shrink-0 text-info"
          aria-hidden="true"
        />
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium">{t('token.boundaryTitle')}</p>
          <p className="mt-1 text-xs leading-5 text-muted-foreground">
            {t('token.boundaryDescription')}
          </p>
        </div>
      </div>
      <section
        className="overflow-hidden rounded-xl border bg-card"
        aria-labelledby="token-list-title"
      >
        <div className="flex flex-wrap items-center justify-between gap-3 border-b p-4">
          <div className="flex items-center gap-2">
            <h2 id="token-list-title" className="text-lg font-semibold">
              {t('token.title')}
            </h2>
            {tokenPage && (
              <span className="rounded-full bg-muted px-2 py-0.5 text-xs tabular-nums text-muted-foreground">
                {tokenPage.total}
              </span>
            )}
          </div>
          <CreateTokenDialog existingNames={tokens.map((token) => token.name)}>
            <Button data-analytics-id="account.token.open">
              <Plus className="h-4 w-4" aria-hidden="true" />
              {t('token.createNew')}
            </Button>
          </CreateTokenDialog>
          <form
            className="flex w-full flex-wrap items-center gap-3"
            onSubmit={(event) => {
              event.preventDefault()
              setQuery(searchInput.trim())
              setPage(0)
            }}
          >
            <div className="relative min-w-0 flex-1 sm:max-w-72">
              <Input
                aria-label={t('token.search')}
                placeholder={t('token.search')}
                value={searchInput}
                onChange={(event) => {
                  setSearchInput(event.target.value)
                  if (!event.target.value) {
                    setQuery('')
                    setPage(0)
                  }
                }}
                className="pr-9"
              />
              <Button
                type="submit"
                variant="ghost"
                size="icon"
                aria-label={t('token.searchAction')}
                className="absolute right-0.5 top-0.5"
              >
                <Search className="h-4 w-4" aria-hidden="true" />
              </Button>
            </div>
            <Select
              value={status}
              onValueChange={(value) => {
                setStatus(value as typeof status)
                setPage(0)
              }}
            >
              <SelectTrigger
                aria-label={t('token.statusFilter')}
                className="w-[180px] max-w-full"
              >
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(['ALL', 'ACTIVE', 'EXPIRED'] as const).map((value) => (
                  <SelectItem key={value} value={value}>
                    {t(
                      `token.${{ ALL: 'statusAll', ACTIVE: 'statusActive', EXPIRED: 'statusExpired' }[value]}`,
                    )}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {(query || status !== 'ALL') && (
              <Button type="button" variant="ghost" onClick={clearFilters}>
                {t('token.clearFilters')}
              </Button>
            )}
          </form>
        </div>
        {isError && (
          <div
            role="alert"
            className="flex items-center justify-between gap-3 border-b bg-destructive/5 p-4 text-sm text-destructive"
          >
            <span className="flex items-start gap-2">
              <AlertCircle className="h-4 w-4 shrink-0" aria-hidden="true" />
              {error instanceof Error ? error.message : t('apiError.unknown')}
            </span>
            <Button
              size="sm"
              variant="outline"
              onClick={() => {
                void refetch()
              }}
            >
              {t('token.retry')}
            </Button>
          </div>
        )}
        {isLoading ? (
          <div
            role="status"
            aria-label={t('token.loading')}
            className="space-y-4 p-5"
          >
            {[0, 1, 2].map((row) => (
              <div
                key={row}
                className="h-12 rounded-lg bg-muted motion-safe:animate-pulse"
              />
            ))}
          </div>
        ) : tokenPage && tokenPage.total === 0 ? (
          <div className="px-5 py-12 text-center">
            <KeyRound
              className="mx-auto mb-3 h-7 w-7 text-muted-foreground"
              aria-hidden="true"
            />
            <p className="text-sm font-medium">
              {t(query || status !== 'ALL' ? 'token.noResults' : 'token.empty')}
            </p>
            <p className="mt-2 text-xs text-muted-foreground">
              {t('token.emptyHint')}
            </p>
            {(query || status !== 'ALL') && (
              <Button variant="outline" onClick={clearFilters} className="mt-4">
                {t('token.clearFilters')}
              </Button>
            )}
          </div>
        ) : tokenPage ? (
          <div aria-busy={isFetching}>
            <Table className="min-w-[840px]">
              <TableHeader>
                <TableRow>
                  <TableHead className={TOKEN_TABLE_HEAD_CLASS_NAME}>
                    {t('token.nameAndPrefix')}
                  </TableHead>
                  <TableHead className={TOKEN_TABLE_HEAD_CLASS_NAME}>
                    {t('token.capabilities')}
                  </TableHead>
                  <TableHead className={TOKEN_TABLE_HEAD_CLASS_NAME}>
                    {t('token.createdAt')}
                  </TableHead>
                  <TableHead className={TOKEN_TABLE_HEAD_CLASS_NAME}>
                    {t('token.lastUsed')}
                  </TableHead>
                  <TableHead className={TOKEN_TABLE_HEAD_CLASS_NAME}>
                    {t('token.expiresAt')}
                  </TableHead>
                  <TableHead className={TOKEN_TABLE_ACTIONS_HEAD_CLASS_NAME}>
                    {t('token.actions')}
                  </TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {tokens.map((token) => {
                  const preset = getTokenPreset(token.capabilities)
                  const Icon =
                    preset === 'readonly'
                      ? Download
                      : preset === 'candidate'
                        ? Upload
                        : KeyRound
                  const expired =
                    !!token.expiresAt &&
                    new Date(token.expiresAt).getTime() <= Date.now()
                  return (
                    <TableRow key={token.id}>
                      <TableCell className="max-w-60">
                        <div className="flex items-start gap-3">
                          <Icon
                            className="mt-1 h-4 w-4 shrink-0 text-muted-foreground"
                            aria-hidden="true"
                          />
                          <div className="min-w-0">
                            <p
                              className="break-all text-sm font-medium"
                              title={token.name}
                            >
                              {token.name}
                            </p>
                            <code className="mt-1 block text-xs text-muted-foreground">
                              {token.tokenPrefix}…
                            </code>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell className="max-w-64">
                        <p className="text-sm font-medium">
                          {preset
                            ? t(`createToken.presets.${preset}`)
                            : t('token.capabilitiesUnavailable')}
                        </p>
                        {token.capabilities ? (
                          <TokenPermissionDetails
                            scopes={token.capabilities}
                            name={token.name}
                            legacyCompat={token.legacyCompat}
                          />
                        ) : (
                          <p className="mt-1 text-xs text-muted-foreground">
                            {t('token.capabilitiesUnavailable')}
                          </p>
                        )}
                        {token.legacyCompat && (
                          <span className="mt-1 block text-xs text-warning-foreground dark:text-warning">
                            {t('token.legacyBadge')}
                          </span>
                        )}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
                        {formatDate(token.createdAt)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-xs text-muted-foreground">
                        {formatDate(token.lastUsedAt)}
                      </TableCell>
                      <TableCell className="whitespace-nowrap text-xs">
                        <p>
                          {formatDate(token.expiresAt, t('token.neverExpires'))}
                        </p>
                        <p
                          className={cn(
                            'mt-1',
                            expired
                              ? 'text-destructive'
                              : token.expiresAt
                                ? 'text-success'
                                : 'text-muted-foreground',
                          )}
                        >
                          {t(
                            expired
                              ? 'token.statusExpired'
                              : token.expiresAt
                                ? 'token.statusActive'
                                : 'token.statusPermanent',
                          )}
                        </p>
                      </TableCell>
                      <TableCell>
                        <div className="flex justify-center gap-1">
                          <Button
                            data-analytics-id="account.token.expiration.edit"
                            variant="ghost"
                            size="sm"
                            onClick={() => handleEditExpiration(token)}
                            disabled={updateExpirationMutation.isPending}
                          >
                            {t('token.editExpiration')}
                          </Button>
                          <Button
                            data-analytics-id="account.token.revoke"
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:text-destructive"
                            onClick={() => handleDelete(token.id, token.name)}
                            disabled={deleteMutation.isPending}
                          >
                            {t('token.delete')}
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
            <div className="flex flex-wrap items-center justify-between gap-3 border-t px-4 py-3">
              <p
                role="status"
                className="text-xs tabular-nums text-muted-foreground"
              >
                {isFetching
                  ? t('token.pageLoading', { page: page + 1 })
                  : t('token.resultRange', {
                      from: tokenPage.total ? displayPage * PAGE_SIZE + 1 : 0,
                      to: Math.min(
                        (displayPage + 1) * PAGE_SIZE,
                        tokenPage.total,
                      ),
                      total: tokenPage.total,
                    })}
              </p>
              {tokenPage.total > PAGE_SIZE && (
                <Pagination
                  analyticsId="account.token.paginate"
                  compact
                  mobileCompact
                  disabled={isFetching}
                  page={displayPage}
                  totalPages={totalPages}
                  onPageChange={setPage}
                />
              )}
            </div>
          </div>
        ) : null}
      </section>
      <div className="flex flex-wrap items-start justify-between gap-4 rounded-xl bg-muted/40 p-5">
        <div className="flex min-w-0 flex-1 gap-3">
          <Terminal className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
          <div>
            <h3 className="text-sm font-semibold">{t('token.usageTitle')}</h3>
            <p className="mt-2 text-xs leading-5 text-muted-foreground">
              {t('token.usageDescription')}
            </p>
            <p className="mt-1 text-xs leading-5 text-muted-foreground">
              {t('token.copyHint')}
            </p>
          </div>
        </div>
        <Button variant="outline" onClick={() => setHelpOpen(true)}>
          <BookOpen className="h-4 w-4" aria-hidden="true" />
          {t('token.capabilityHelp')}
        </Button>
      </div>
      <Dialog open={helpOpen} onOpenChange={setHelpOpen}>
        <DialogContent className="w-[min(calc(100vw-2rem),42.5rem)]">
          <DialogHeader>
            <DialogTitle>{t('token.helpTitle')}</DialogTitle>
            <DialogDescription>{t('token.helpDescription')}</DialogDescription>
          </DialogHeader>
          <dl className="space-y-4">
            {TOKEN_CAPABILITIES.map(({ scope, label }) => (
              <div key={scope} className="min-w-0">
                <dt className="text-sm font-medium">
                  {t(`token.capabilityLabels.${label}`)}{' '}
                  <code className="break-words text-xs font-normal text-muted-foreground">
                    {scope}
                  </code>
                </dt>
                <dd className="mt-1 text-xs leading-5 text-muted-foreground">
                  {t(`createToken.capabilityDescriptions.${label}`)}
                </dd>
              </div>
            ))}
          </dl>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        confirmAnalyticsId="account.token.revoke.confirm"
        cancelAnalyticsId="account.token.revoke.cancel"
        closeOnConfirm={false}
        open={deleteDialog.open}
        onOpenChange={(open) => setDeleteDialog({ ...deleteDialog, open })}
        title={t('token.deleteTitle')}
        description={
          <>
            <span className="block">{t('token.deleteDescription')}</span>
            <span className="my-4 flex items-center gap-3 rounded-lg border bg-muted/40 p-4 text-left">
              <Trash2
                className="h-5 w-5 shrink-0 text-destructive"
                aria-hidden="true"
              />
              <span className="min-w-0 break-all font-medium text-foreground">
                {deleteDialog.name}
              </span>
            </span>
            <span className="block text-xs">{t('token.revokeHint')}</span>
          </>
        }
        confirmText={t('token.delete')}
        variant="destructive"
        onConfirm={confirmDelete}
      />

      <Dialog
        open={expirationDialog.open}
        onOpenChange={(open) =>
          setExpirationDialog((current) => ({ ...current, open }))
        }
      >
        <DialogContent closeAnalyticsId="account.token.expiration.cancel">
          <DialogHeader className="min-w-0">
            <DialogTitle>{t('token.editExpirationTitle')}</DialogTitle>
            <DialogDescription className="break-all">
              {t('token.editExpirationDescription', {
                name: expirationDialog.tokenName,
              })}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="token-expiration-mode">
                {t('createToken.expirationLabel')}
              </Label>
              <Select
                value={expirationDialog.mode}
                onValueChange={(value) =>
                  setExpirationDialog((current) => ({
                    ...current,
                    mode: value as TokenExpirationMode,
                  }))
                }
              >
                <SelectTrigger
                  data-analytics-id="account.token.edit.expiry.interact"
                  id="token-expiration-mode"
                  className="h-9 text-xs sm:h-7 sm:w-[12.5rem]"
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent density="compact">
                  <SelectItem
                    data-analytics-id="account.token.edit.expiry.never"
                    value="never"
                  >
                    {t('createToken.expirationNever')}
                  </SelectItem>
                  <SelectItem
                    data-analytics-id="account.token.edit.expiry.week"
                    value="7d"
                  >
                    {t('createToken.expiration7d')}
                  </SelectItem>
                  <SelectItem
                    data-analytics-id="account.token.edit.expiry.month"
                    value="30d"
                  >
                    {t('createToken.expiration30d')}
                  </SelectItem>
                  <SelectItem
                    data-analytics-id="account.token.edit.expiry.quarter"
                    value="90d"
                  >
                    {t('createToken.expiration90d')}
                  </SelectItem>
                  <SelectItem
                    data-analytics-id="account.token.edit.expiry.custom"
                    value="custom"
                  >
                    {t('createToken.expirationCustom')}
                  </SelectItem>
                </SelectContent>
              </Select>
            </div>
            {expirationDialog.mode === 'custom' ? (
              <div className="space-y-2">
                <Label htmlFor="token-expiration-custom">
                  {t('createToken.expirationCustom')}
                </Label>
                <DateTimePicker
                  id="token-expiration-custom"
                  aria-label={t('createToken.expirationCustom')}
                  disabled={updateExpirationMutation.isPending}
                  value={expirationDialog.customExpiresAt}
                  min={new Date()}
                  onChange={(value) =>
                    setExpirationDialog((current) => ({
                      ...current,
                      customExpiresAt: value,
                    }))
                  }
                />
              </div>
            ) : null}
          </div>
          <DialogFooter>
            <Button
              data-analytics-id="account.token.expiration.cancel"
              variant="outline"
              onClick={() =>
                setExpirationDialog({
                  open: false,
                  mode: 'never',
                  customExpiresAt: '',
                })
              }
            >
              {t('dialog.cancel')}
            </Button>
            <Button
              data-analytics-id="account.token.expiration.save"
              onClick={confirmExpirationUpdate}
              disabled={updateExpirationMutation.isPending}
            >
              {updateExpirationMutation.isPending
                ? t('token.updatingExpiration')
                : t('token.saveExpiration')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
