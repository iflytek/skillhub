import { Link } from '@tanstack/react-router'
import { useState } from 'react'
import { LoginButton } from '@/features/auth/login-button'
import { useLocalRegister } from '@/features/auth/use-local-auth'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'

export function RegisterPage() {
  const registerMutation = useLocalRegister()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await registerMutation.mutateAsync({ username, email, password })
      window.location.href = '/dashboard'
    } catch {
      // mutation state drives the error UI
    }
  }

  return (
    <div className="mx-auto flex min-h-[70vh] max-w-2xl items-center justify-center">
      <Card className="w-full border-slate-200 bg-white/95 shadow-xl">
        <CardHeader className="space-y-3 text-center">
          <CardTitle>创建账号</CardTitle>
          <CardDescription>支持本地注册，也可以直接使用 OAuth 登录进入平台。</CardDescription>
        </CardHeader>
        <CardContent>
          <Tabs defaultValue="local" className="space-y-6">
            <TabsList className="grid w-full grid-cols-2">
              <TabsTrigger value="local">本地账号</TabsTrigger>
              <TabsTrigger value="oauth">OAuth</TabsTrigger>
            </TabsList>

            <TabsContent value="local">
              <form className="space-y-4" onSubmit={handleSubmit}>
                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor="register-username">用户名</label>
                  <Input
                    id="register-username"
                    autoComplete="username"
                    value={username}
                    onChange={(event) => setUsername(event.target.value)}
                    placeholder="3-64 位字母、数字或下划线"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor="register-email">邮箱</label>
                  <Input
                    id="register-email"
                    type="email"
                    autoComplete="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="可选，用于后续账号识别"
                  />
                </div>
                <div className="space-y-2">
                  <label className="text-sm font-medium" htmlFor="register-password">密码</label>
                  <Input
                    id="register-password"
                    type="password"
                    autoComplete="new-password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="至少 8 位，包含 3 种字符类型"
                  />
                </div>
                {registerMutation.error ? (
                  <p className="text-sm text-red-600">{registerMutation.error.message}</p>
                ) : null}
                <Button className="w-full" disabled={registerMutation.isPending} type="submit">
                  {registerMutation.isPending ? '注册中...' : '注册并登录'}
                </Button>
                <p className="text-center text-sm text-muted-foreground">
                  已有账号？
                  {' '}
                  <Link to="/login" className="font-medium text-primary hover:underline">
                    返回登录
                  </Link>
                </p>
              </form>
            </TabsContent>

            <TabsContent value="oauth" className="space-y-4">
              <p className="text-sm text-muted-foreground">
                直接使用现有 OAuth 账户进入平台，无需再创建本地密码。
              </p>
              <LoginButton />
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  )
}
