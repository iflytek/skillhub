import { Link } from '@tanstack/react-router'
import { useState } from 'react'
import { LoginButton } from '@/features/auth/login-button'
import { useLocalLogin } from '@/features/auth/use-local-auth'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'

export function LoginPage() {
  const loginMutation = useLocalLogin()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await loginMutation.mutateAsync({ username, password })
      window.location.href = '/dashboard'
    } catch {
      // mutation state drives the error UI
    }
  }

  return (
    <div className="flex min-h-[70vh] items-center justify-center">
      <div className="w-full max-w-md space-y-8 animate-fade-up">
        <div className="text-center space-y-3">
          <div className="inline-flex w-16 h-16 rounded-2xl bg-gradient-to-br from-primary to-primary/70 items-center justify-center shadow-glow mb-4">
            <span className="text-primary-foreground font-bold text-2xl">S</span>
          </div>
          <h1 className="text-4xl font-bold font-heading text-foreground">登录 SkillHub</h1>
          <p className="text-muted-foreground text-lg">
            选择一个方式登录以继续
          </p>
        </div>

        <div className="glass-strong p-8 rounded-2xl">
          <Tabs defaultValue="password" className="space-y-6">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="password">账号密码</TabsTrigger>
              <TabsTrigger value="oauth">GitHub</TabsTrigger>
            </TabsList>

            <TabsContent value="password">
              <form className="space-y-4" onSubmit={handleSubmit}>
                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor="username">用户名</label>
                  <Input
                    id="username"
                    autoComplete="username"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    placeholder="输入用户名"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor="password">密码</label>
                  <Input
                    id="password"
                    type="password"
                    autoComplete="current-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="输入密码"
                  />
                </div>
                {loginMutation.error ? (
                  <p className="text-sm text-red-600">{loginMutation.error.message}</p>
                ) : null}
                <Button className="w-full" disabled={loginMutation.isPending} type="submit">
                  {loginMutation.isPending ? '登录中...' : '登录'}
                </Button>
                <p className="text-center text-sm text-muted-foreground">
                  还没有账号？
                  {' '}
                  <Link to="/register" className="font-medium text-primary hover:underline">
                    立即注册
                  </Link>
                </p>
              </form>
            </TabsContent>

            <TabsContent value="oauth" className="space-y-4">
              <p className="text-sm text-muted-foreground">
                使用 GitHub 登录时，认证完成后会自动返回当前站点。
              </p>
              <LoginButton />
            </TabsContent>
          </Tabs>
        </div>

        <p className="text-center text-xs text-muted-foreground">
          登录即表示你同意我们的
          <a href="#" className="text-primary hover:underline ml-1">服务条款</a>
          和
          <a href="#" className="text-primary hover:underline ml-1">隐私政策</a>
        </p>
      </div>
    </div>
  )
}
