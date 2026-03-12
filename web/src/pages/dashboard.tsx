import { useAuth } from '@/features/auth/use-auth'
import { TokenList } from '@/features/token/token-list'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'

export function DashboardPage() {
  const { user } = useAuth()

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="text-4xl font-bold font-heading text-foreground">Dashboard</h1>
        <p className="text-muted-foreground mt-2 text-lg">
          管理你的账户和 API Tokens
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>用户信息</CardTitle>
          <CardDescription>你的账户详情</CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="flex items-center gap-5">
            {user?.avatarUrl && (
              <img
                src={user.avatarUrl}
                alt={user.displayName}
                className="h-20 w-20 rounded-2xl border-2 border-border/60 shadow-card"
              />
            )}
            <div className="space-y-1.5">
              <div className="text-xl font-semibold font-heading">{user?.displayName}</div>
              <div className="text-sm text-muted-foreground">{user?.email}</div>
              <div className="text-xs text-muted-foreground flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-emerald-500" />
                通过 {user?.oauthProvider} 登录
              </div>
            </div>
          </div>
          {user?.platformRoles && user.platformRoles.length > 0 && (
            <div className="space-y-3">
              <div className="text-sm font-medium font-heading">平台角色</div>
              <div className="flex flex-wrap gap-2">
                {user.platformRoles.map((role) => (
                  <span
                    key={role}
                    className="inline-flex items-center rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary border border-primary/20"
                  >
                    {role}
                  </span>
                ))}
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <TokenList />
    </div>
  )
}
