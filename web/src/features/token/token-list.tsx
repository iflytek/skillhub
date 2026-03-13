import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
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
import type { ApiToken } from '@/api/types'

export function TokenList() {
  const queryClient = useQueryClient()

  const { data: tokens, isLoading } = useQuery<ApiToken[]>({
    queryKey: ['tokens'],
    queryFn: tokenApi.getTokens,
  })

  const deleteMutation = useMutation({
    mutationFn: (tokenId: number) => tokenApi.deleteToken(tokenId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tokens'] })
    },
  })

  const handleDelete = (tokenId: number, name: string) => {
    if (window.confirm(`确定要删除 Token "${name}" 吗？`)) {
      deleteMutation.mutate(tokenId)
    }
  }

  const formatDate = (dateString?: string | null) => {
    if (!dateString) return '-'
    return new Date(dateString).toLocaleString('zh-CN')
  }

  if (isLoading) {
    return <div className="text-center py-8 text-muted-foreground">加载中...</div>
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold">API Tokens</h2>
        <CreateTokenDialog>
          <Button>创建新 Token</Button>
        </CreateTokenDialog>
      </div>

      {!tokens || tokens.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground">
          <p>还没有创建任何 Token</p>
          <p className="text-sm mt-2">点击上方按钮创建第一个 Token</p>
        </div>
      ) : (
        <div className="border rounded-lg">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>名称</TableHead>
                <TableHead>Token 前缀</TableHead>
                <TableHead>创建时间</TableHead>
                <TableHead>最后使用</TableHead>
                <TableHead>过期时间</TableHead>
                <TableHead className="text-right">操作</TableHead>
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
                      删除
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
    </div>
  )
}
