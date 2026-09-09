import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { canViewGovernanceCenter } from '@/shared/lib/governance-access'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import {
  Star, Heart, Package, Key, Shield, Flag, Globe,
  UserCog, Lock, Bell, Clock, ChevronRight,
} from 'lucide-react'

/**
 * Sidebar navigation groups for the Dashboard.
 *
 * Groups are rendered with a label divider; items within each group share visual spacing.
 * Admin-only items are filtered based on the user's platform roles.
 */

interface SidebarItem {
  key: string
  icon: React.ComponentType<{ className?: string }>
  label: string
  to: string
  admin?: boolean
  passwordCapability?: boolean
  badge?: string
}

interface SidebarGroup {
  label?: string
  items: SidebarItem[]
}

export const SIDEBAR_GROUPS: SidebarGroup[] = [
  {
    label: 'sidebar.account',
    items: [
      { key: 'profile', icon: UserCog, label: 'sidebar.profile', to: '/settings/profile' },
      { key: 'security', icon: Lock, label: 'sidebar.security', to: '/settings/security', passwordCapability: true },
      { key: 'notifications', icon: Bell, label: 'sidebar.notifications', to: '/settings/notifications' },
    ],
  },
  {
    label: 'sidebar.skillsAndData',
    items: [
      { key: 'skills', icon: Package, label: 'sidebar.mySkills', to: '/dashboard/skills' },
      { key: 'namespaces', icon: Globe, label: 'sidebar.namespaces', to: '/dashboard/namespaces' },
      { key: 'stars', icon: Star, label: 'sidebar.stars', to: '/dashboard/stars' },
      { key: 'subscriptions', icon: Heart, label: 'sidebar.subscriptions', to: '/dashboard/subscriptions' },
      { key: 'tokens', icon: Key, label: 'sidebar.tokens', to: '/dashboard/tokens' },
      { key: 'reviewProgress', icon: Clock, label: 'sidebar.reviewProgress', to: '/dashboard/review-progress' },
    ],
  },
  {
    label: 'sidebar.admin',
    items: [
      { key: 'governance', icon: Shield, label: 'sidebar.governance', to: '/dashboard/governance', admin: true },
      { key: 'reports', icon: Flag, label: 'sidebar.reports', to: '/dashboard/reports', admin: true },
    ],
  },
]

// Flatten for backward compatibility with layout.tsx
const SIDEBAR_NAV_ITEMS = SIDEBAR_GROUPS.flatMap((g) => g.items)

export const SIDEBAR_NAV = SIDEBAR_NAV_ITEMS.map(({ key, icon, label, to, admin, passwordCapability }) => ({
  key,
  icon,
  label,
  to,
  admin,
  passwordCapability,
  exact: false,
}))

/**
 * Overview cards for the Dashboard home.
 * Shown as a grid of quick-access cards linking to main sections.
 */
const OVERVIEW_CARDS = [
  { key: 'skills', icon: Package, label: 'overview.mySkills', to: '/dashboard/skills', desc: 'overview.mySkillsDesc' },
  { key: 'tokens', icon: Key, label: 'overview.tokens', to: '/dashboard/tokens', desc: 'overview.tokensDesc' },
  { key: 'stars', icon: Star, label: 'overview.stars', to: '/dashboard/stars', desc: 'overview.starsDesc' },
  { key: 'profile', icon: UserCog, label: 'overview.profile', to: '/settings/profile', desc: 'overview.profileDesc' },
  { key: 'security', icon: Lock, label: 'overview.security', to: '/settings/security', desc: 'overview.securityDesc' },
] as const

/**
 * Dashboard home page with sidebar + overview cards.
 */
export function DashboardPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const governanceVisible = canViewGovernanceCenter(user?.platformRoles)

  const filteredGroups = SIDEBAR_GROUPS
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => (
        (!item.admin || governanceVisible)
        && (!item.passwordCapability || user?.canChangePassword === true)
      )),
    }))
    .filter((group) => group.items.length > 0)

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <DashboardPageHeader title={t('dashboard.title')} subtitle={t('dashboard.subtitle')} />
      {/* Two-column layout */}
      <div className="flex flex-col lg:flex-row gap-6">
        {/* Left sidebar */}
        <DashboardSidebar groups={filteredGroups} user={user} t={t} pathname="/dashboard" />

        {/* Right content - overview cards */}
        <div className="flex-1 min-w-0">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            {OVERVIEW_CARDS.filter((card) => card.key !== 'security' || user?.canChangePassword === true).map((card) => {
              const Icon = card.icon
              return (
                <Link
                  key={card.key}
                  to={card.to}
                  className="group flex items-start gap-4 rounded-xl border border-border/60 p-5 transition-all duration-150 hover:bg-accent hover:border-border hover:shadow-sm"
                >
                  <div className="flex-shrink-0 w-10 h-10 rounded-lg flex items-center justify-center bg-secondary">
                    <Icon className="w-5 h-5" style={{ color: 'hsl(var(--foreground))' }} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold" style={{ color: 'hsl(var(--foreground))' }}>
                        {t(card.label)}
                      </span>
                      <ChevronRight className="w-3.5 h-3.5 opacity-0 -translate-x-1 transition-all duration-150 group-hover:opacity-100 group-hover:translate-x-0" style={{ color: 'hsl(var(--muted-foreground))' }} />
                    </div>
                    <p className="mt-1 text-xs leading-relaxed" style={{ color: 'hsl(var(--text-secondary))' }}>
                      {t(card.desc)}
                    </p>
                  </div>
                </Link>
              )
            })}
          </div>
        </div>
      </div>
    </div>
  )
}

/** Reusable sidebar for dashboard and its sub-pages. */
export function DashboardSidebar({
  groups,
  user,
  t,
  pathname,
}: {
  groups: SidebarGroup[]
  user: ReturnType<typeof useAuth>['user']
  t: ReturnType<typeof useTranslation>['t']
  pathname: string
}) {
  return (
    <aside className="w-full lg:w-56 flex-shrink-0">
      <div className="lg:sticky lg:top-[68px]">
      {/* User summary */}
      <div className="flex items-center gap-3 px-3 py-2 mb-4">
        {user?.avatarUrl ? (
          <img src={user.avatarUrl} alt={user.displayName} className="h-8 w-8 rounded-full border border-border/60" />
        ) : (
          <div className="h-8 w-8 rounded-full bg-secondary flex items-center justify-center text-xs font-semibold" style={{ color: 'hsl(var(--foreground))' }}>
            {user?.displayName?.charAt(0) ?? '?'}
          </div>
        )}
        <div className="min-w-0">
          <div className="text-sm font-semibold truncate" style={{ color: 'hsl(var(--foreground))' }}>
            {user?.displayName}
          </div>
          <div className="text-xs truncate" style={{ color: 'hsl(var(--muted-foreground))' }}>
            {user?.email}
          </div>
          <div className="text-xs truncate" style={{ color: 'hsl(var(--muted-foreground))' }}>
            {t('dashboard.userId')}: {user?.userId}
          </div>
        </div>
      </div>

      {/* Nav groups */}
      {groups.map((group) => (
        <div key={group.label ?? 'top'} className="mb-4">
          {group.label && (
            <div className="px-3 py-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color: 'hsl(var(--muted-foreground))' }}>
              {t(group.label)}
            </div>
          )}
          <nav className="space-y-0.5">
            {group.items.map((item) => {
              const Icon = item.icon
              const isActive = pathname === item.to || pathname.startsWith(item.to)
              return (
                <Link
                  key={item.key}
                  to={item.to}
                  className={`flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-medium transition-colors duration-150 ${
                    isActive ? 'bg-accent' : 'hover:bg-accent'
                  }`}
                  style={{ color: isActive ? 'hsl(var(--foreground))' : 'hsl(var(--text-secondary))' }}
                >
                  <Icon className="w-4 h-4 flex-shrink-0" />
                  <span>{t(item.label)}</span>
                </Link>
              )
            })}
          </nav>
        </div>
      ))}
      </div>
    </aside>
  )
}
