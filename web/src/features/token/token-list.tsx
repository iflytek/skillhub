import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Pencil } from 'lucide-react'
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
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { Select } from '@/shared/ui/select'
import { Input } from '@/shared/ui/input'
import type { ApiToken } from '@/api/types'
import { resolveTokenExpiresAt, toLocalDateTimeInputValue, type TokenExpirationMode } from './token-expiration'

const PAGE_SIZE = 10
type TokenPage = { items: ApiToken[]; total: number; page: number; size: number }

export function TokenList() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [deleteDialog, setDeleteDialog] = useState<{ open: boolean; tokenId?: number; name?: string }>({
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

  const { data: tokenPage, isLoading, isError, error } = useQuery<TokenPage>({
    queryKey: ['tokens', page, PAGE_SIZE],
    queryFn: () => tokenApi.getTokens({ page, size: PAGE_SIZE }),
    meta: {
      skipGlobalErrorHandler: true,
    },
  })

  const updateExpirationMutation = useMutation({
    mutationFn: ({ tokenId, expiresAt }: { tokenId: number; expiresAt?: string }) =>
      tokenApi.updateTokenExpiration(tokenId, expiresAt),
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
      await queryClient.cancelQueries({ queryKey: ['tokens'] })

      const previousPages = queryClient.getQueriesData<TokenPage>({ queryKey: ['tokens'] })
      queryClient.setQueriesData<TokenPage>({ queryKey: ['tokens'] }, (current) => {
        if (!current) {
          return current
        }

        const nextItems = current.items.filter((token) => token.id !== tokenId)
        if (nextItems.length === current.items.length) {
          return current
        }

        return {
          ...current,
          items: nextItems,
          total: Math.max(current.total - 1, 0),
        }
      })

      return { previousPages }
    },
    onSuccess: () => {
      if (tokenPage && tokenPage.items.length === 1 && page > 0) {
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
      customExpiresAt: token.expiresAt ? token.expiresAt.slice(0, 16) : '',
    })
  }

  const confirmDelete = async () => {
    if (deleteDialog.tokenId) {
      await deleteMutation.mutateAsync(deleteDialog.tokenId)
    }
  }

  const confirmExpirationUpdate = async () => {
    if (!expirationDialog.tokenId) {
      return
    }

    const expiresAt = resolveTokenExpiresAt(expirationDialog.mode, expirationDialog.customExpiresAt)
    if (expirationDialog.mode === 'custom' && !expiresAt) {
      toast.error(t('createToken.expiresAtRequired'))
      return
    }

    await updateExpirationMutation.mutateAsync({
      tokenId: expirationDialog.tokenId,
      expiresAt,
    })
  }

  const formatDate = (dateString?: string | null, emptyLabel = '-') => {
    if (!dateString) return emptyLabel
    return formatLocalDateTime(dateString, i18n.language)
  }

  const tokens = tokenPage?.items ?? []
  const totalPages = tokenPage ? Math.max(Math.ceil(tokenPage.total / tokenPage.size), 1) : 1

  if (isLoading) {
    return <div className="text-center py-8 text-muted-foreground">{t('token.loading')}</div>
  }

  if (isError) {
    return (
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-semibold">{t('token.title')}</h2>
          <CreateTokenDialog existingNames={[]}>
            <Button>{t('token.createNew')}</Button>
          </CreateTokenDialog>
        </div>
        <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-6 text-sm text-destructive">
          {error instanceof Error ? error.message : t('apiError.unknown')}
        </div>
      </div>
    )
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">{t('token.title')}</h2>
        <CreateTokenDialog existingNames={tokens.map((token) => token.name)}>
          <Button>{t('token.createNew')}</Button>
        </CreateTokenDialog>
      </div>
      <p className="text-sm text-muted-foreground">{t('token.copyHint')}</p>

      {!tokenPage || tokenPage.total === 0 ? (
        <div className="text-center py-12 text-muted-foreground">
          <p>{t('token.empty')}</p>
          <p className="text-sm mt-2">{t('token.emptyHint')}</p>
        </div>
      ) : (
        <div className="border rounded-lg">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('token.name')}</TableHead>
                <TableHead>{t('token.prefix')}</TableHead>
                <TableHead>{t('token.createdAt')}</TableHead>
                <TableHead>{t('token.lastUsed')}</TableHead>
                <TableHead>{t('token.expiresAt')}</TableHead>
                <TableHead className="text-center">{t('token.actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tokens.map((token) => (
                <TableRow key={token.id}>
                  <TableCell className="font-medium max-w-48 break-all">{token.name}</TableCell>
                  <TableCell>
                    <code className="text-sm bg-muted px-2 py-1 rounded">
                      {token.tokenPrefix}...
                    </code>
                  </TableCell>
                  <TableCell>{formatDate(token.createdAt)}</TableCell>
                  <TableCell>{formatDate(token.lastUsedAt)}</TableCell>
                  <TableCell>{formatDate(token.expiresAt, t('token.neverExpires'))}</TableCell>
                  <TableCell className="text-center">
                    <div className="flex justify-center gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => handleEditExpiration(token)}
                        disabled={updateExpirationMutation.isPending}
                      >
                        <Pencil className="mr-1 h-4 w-4" />
                        {t('token.editExpiration')}
                      </Button>
                      <Button
                        variant="destructive"
                        size="sm"
                        onClick={() => handleDelete(token.id, token.name)}
                        disabled={deleteMutation.isPending}
                      >
                        {t('token.delete')}
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      {tokenPage && tokenPage.total > PAGE_SIZE ? (
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      ) : null}

      <ConfirmDialog
        open={deleteDialog.open}
        onOpenChange={(open) => setDeleteDialog({ ...deleteDialog, open })}
        title={t('token.deleteTitle')}
        description={t('token.deleteDescription', { name: deleteDialog.name })}
        confirmText={t('dialog.delete')}
        variant="destructive"
        onConfirm={confirmDelete}
      />

      <Dialog open={expirationDialog.open} onOpenChange={(open) => setExpirationDialog((current) => ({ ...current, open }))}>
        <DialogContent>
          <DialogHeader className="min-w-0">
            <DialogTitle>{t('token.editExpirationTitle')}</DialogTitle>
            <DialogDescription className="break-all">
              {t('token.editExpirationDescription', { name: expirationDialog.tokenName })}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="token-expiration-mode">{t('createToken.expirationLabel')}</Label>
              <Select
                id="token-expiration-mode"
                value={expirationDialog.mode}
                onChange={(event) => setExpirationDialog((current) => ({
                  ...current,
                  mode: event.target.value as TokenExpirationMode,
                }))}
              >
                <option value="never">{t('createToken.expirationNever')}</option>
                <option value="7d">{t('createToken.expiration7d')}</option>
                <option value="30d">{t('createToken.expiration30d')}</option>
                <option value="90d">{t('createToken.expiration90d')}</option>
                <option value="custom">{t('createToken.expirationCustom')}</option>
              </Select>
            </div>
            {expirationDialog.mode === 'custom' ? (
              <div className="space-y-2">
                <Label htmlFor="token-expiration-custom">{t('createToken.expirationCustom')}</Label>
                <Input
                  id="token-expiration-custom"
                  type="datetime-local"
                  value={expirationDialog.customExpiresAt}
                  min={toLocalDateTimeInputValue(new Date())}
                  onChange={(event) => setExpirationDialog((current) => ({
                    ...current,
                    customExpiresAt: event.target.value,
                  }))}
                />
              </div>
            ) : null}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setExpirationDialog({ open: false, mode: 'never', customExpiresAt: '' })}>
              {t('dialog.cancel')}
            </Button>
            <Button onClick={confirmExpirationUpdate} disabled={updateExpirationMutation.isPending}>
              {updateExpirationMutation.isPending ? t('token.updatingExpiration') : t('token.saveExpiration')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
