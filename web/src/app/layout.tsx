import { Outlet, Link } from '@tanstack/react-router'
import { useAuth } from '@/features/auth/use-auth'

export function Layout() {
  const { user, isLoading } = useAuth()

  return (
    <div className="min-h-screen bg-background bg-dots relative">
      {/* Glow orbs */}
      <div className="glow-orb-primary" style={{ top: '-10%', right: '10%' }} />
      <div className="glow-orb-accent" style={{ bottom: '20%', left: '-5%' }} />

      {/* Glass header */}
      <header className="sticky top-0 z-50 glass-strong border-b border-border/40">
        <div className="container mx-auto flex h-16 items-center justify-between px-4 lg:px-8">
          <Link to="/" className="flex items-center gap-2 group">
            <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-primary/70 flex items-center justify-center shadow-glow">
              <span className="text-primary-foreground font-bold text-sm">S</span>
            </div>
            <span className="text-xl font-bold font-heading text-foreground group-hover:text-primary transition-colors">
              SkillHub
            </span>
          </Link>

          <nav className="flex items-center gap-6">
            {isLoading ? null : user ? (
              <>
                <Link
                  to="/dashboard"
                  className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
                  activeProps={{ className: 'text-primary' }}
                >
                  Dashboard
                </Link>
                <div className="flex items-center gap-3">
                  {user.avatarUrl && (
                    <img
                      src={user.avatarUrl}
                      alt={user.displayName}
                      className="w-8 h-8 rounded-full border border-border/60"
                    />
                  )}
                  <span className="text-sm font-medium text-foreground">
                    {user.displayName}
                  </span>
                </div>
              </>
            ) : (
              <Link
                to="/login"
                className="text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
                activeProps={{ className: 'text-primary' }}
              >
                登录
              </Link>
            )}
          </nav>
        </div>
      </header>

      <main className="container mx-auto px-4 lg:px-8 py-12 relative z-10">
        <Outlet />
      </main>

      {/* Footer */}
      <footer className="relative z-10 border-t border-border/40 bg-card/30 backdrop-blur-sm mt-24">
        <div className="container mx-auto px-4 lg:px-8 py-12">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-8 mb-8">
            <div className="col-span-1 md:col-span-2">
              <div className="flex items-center gap-2 mb-4">
                <div className="w-8 h-8 rounded-lg bg-gradient-to-br from-primary to-primary/70 flex items-center justify-center shadow-glow">
                  <span className="text-primary-foreground font-bold text-sm">S</span>
                </div>
                <span className="text-xl font-bold font-heading text-foreground">SkillHub</span>
              </div>
              <p className="text-sm text-muted-foreground max-w-sm">
                现代化的技能注册中心，为开发者提供高效的技能管理和分发平台。
              </p>
            </div>

            <div>
              <h3 className="text-sm font-semibold font-heading text-foreground mb-3">快速链接</h3>
              <ul className="space-y-2">
                <li>
                  <Link to="/" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    首页
                  </Link>
                </li>
                <li>
                  <Link
                    to="/search"
                    search={{ q: '', sort: 'relevance', page: 1 }}
                    className="text-sm text-muted-foreground hover:text-primary transition-colors"
                  >
                    搜索技能
                  </Link>
                </li>
                <li>
                  <Link to="/dashboard" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    Dashboard
                  </Link>
                </li>
              </ul>
            </div>

            <div>
              <h3 className="text-sm font-semibold font-heading text-foreground mb-3">资源</h3>
              <ul className="space-y-2">
                <li>
                  <a href="#" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    文档
                  </a>
                </li>
                <li>
                  <a href="#" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    API
                  </a>
                </li>
                <li>
                  <a href="#" className="text-sm text-muted-foreground hover:text-primary transition-colors">
                    社区
                  </a>
                </li>
              </ul>
            </div>
          </div>

          <div className="pt-6 border-t border-border/40 flex flex-col md:flex-row items-center justify-between gap-4">
            <p className="text-xs text-muted-foreground">
              © 2024 SkillHub. All rights reserved.
            </p>
            <div className="flex items-center gap-4">
              <a href="#" className="text-xs text-muted-foreground hover:text-primary transition-colors">
                隐私政策
              </a>
              <a href="#" className="text-xs text-muted-foreground hover:text-primary transition-colors">
                服务条款
              </a>
            </div>
          </div>
        </div>
      </footer>
    </div>
  )
}
