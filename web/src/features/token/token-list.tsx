import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
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
import { toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import type { ApiToken } from '@/api/types'

const PAGE_SIZE = 10

export function TokenList() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [deleteDialog, setDeleteDialog] = useState<{ open: boolean; tokenId?: number; name?: string }>({
    open: false,
  })

  const { data: tokenPage, isLoading } = useQuery<{ items: ApiToken[]; total: number; page: number; size: number }>({
    queryKey: ['tokens', page, PAGE_SIZE],
    queryFn: () => tokenApi.getTokens({ page, size: PAGE_SIZE }),
  })

  const deleteMutation = useMutation({
    mutationFn: (tokenId: number) => tokenApi.deleteToken(tokenId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tokens'] })
      toast.success(t('token.deleteSuccess'))
    },
    onError: () => {
      toast.error(t('token.deleteFailed'))
    },
  })

  const handleDelete = (tokenId: number, name: string) => {
    setDeleteDialog({ open: true, tokenId, name })
  }

  const confirmDelete = async () => {
    if (deleteDialog.tokenId) {
      deleteMutation.mutate(deleteDialog.tokenId)
    }
  }

  const formatDate = (dateString?: string | null) => {
    if (!dateString) return '-'
    return formatLocalDateTime(dateString, i18n.language)
  }

  const tokens = tokenPage?.items ?? []
  const totalPages = tokenPage ? Math.max(Math.ceil(tokenPage.total / tokenPage.size), 1) : 1

  if (isLoading) {
    return <div className="text-center py-8 text-muted-foreground">{t('token.loading')}</div>
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">{t('token.title')}</h2>
        <CreateTokenDialog existingNames={tokens.map((token) => token.name)}>
          <Button>{t('token.createNew')}</Button>
        </CreateTokenDialog>
      </div>

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
                <TableHead className="text-right">{t('token.actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {tokens.map((token) => (
                <TableRow key={token.id}>
                  <TableCell className="font-medium">{token.name}</TableCell>
                  <TableCell>
                    <code className="text-sm bg-muted px-2 py-1 rounded">
                      {token.tokenPrefix}...
                    </code>
                  </TableCell>
                  <TableCell>{formatDate(token.createdAt)}</TableCell>
                  <TableCell>{formatDate(token.lastUsedAt)}</TableCell>
                  <TableCell>{formatDate(token.expiresAt)}</TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="destructive"
                      size="sm"
                      onClick={() => handleDelete(token.id, token.name)}
                      disabled={deleteMutation.isPending}
                    >
                      {t('token.delete')}
                    </Button>
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
    </div>
  )
}
