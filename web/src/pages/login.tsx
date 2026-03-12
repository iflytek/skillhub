import { LoginButton } from '@/features/auth/login-button'

export function LoginPage() {
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
          <LoginButton />
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
