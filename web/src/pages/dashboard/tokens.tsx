import { TokenList } from '@/features/token/token-list'

export function TokensPage() {
  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading mb-2">Token 管理</h1>
        <p className="text-muted-foreground text-lg">管理 CLI 和 API 使用的访问凭证</p>
      </div>
      <TokenList />
    </div>
  )
}
