import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import { cn } from '@/shared/lib/utils'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/shared/ui/dropdown-menu'

interface User {
  displayName: string
  avatarUrl?: string
  platformRoles?: string[]
}

interface UserMenuProps {
  user: User
  triggerClassName?: string
}

export function UserMenu({ user, triggerClassName }: UserMenuProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()

  const hasRole = (role: string) => user.platformRoles?.includes(role) ?? false
  const isReviewer = hasRole('SKILL_ADMIN') || hasRole('NAMESPACE_ADMIN') || hasRole('SUPER_ADMIN')
  const isSkillAdmin = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const isUserAdmin = hasRole('USER_ADMIN') || hasRole('SUPER_ADMIN')
  const isAuditor = hasRole('AUDITOR') || hasRole('SUPER_ADMIN')

  const handleLogout = async () => {
    try {
      await authApi.logout()
    } catch (error) {
      console.error('Logout failed:', error)
    } finally {
      // Always clear cache and redirect, even if API call fails
      queryClient.setQueryData(['auth', 'me'], null)
      window.location.href = '/'
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button className={cn('flex items-center gap-3 text-foreground hover:opacity-80 transition-opacity', triggerClassName)}>
          {user.avatarUrl && (
            <img
              src={user.avatarUrl}
              alt={user.displayName}
              loading="lazy"
              className="w-8 h-8 rounded-full border border-border/60"
            />
          )}
          <span className="text-sm font-medium text-inherit">
            {user.displayName}
          </span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-48">
        <DropdownMenuItem asChild>
          <Link to="/dashboard" className="cursor-pointer">
            {t('user.menu.dashboard')}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/dashboard/skills" className="cursor-pointer">
            {t('user.menu.mySkills')}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/dashboard/namespaces" className="cursor-pointer">
            {t('user.menu.myNamespaces')}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/dashboard/stars" className="cursor-pointer">
            {t('user.menu.stars')}
          </Link>
        </DropdownMenuItem>
        {isReviewer && (
          <DropdownMenuItem asChild>
            <Link to="/dashboard/reviews" className="cursor-pointer">
              {t('user.menu.reviews')}
            </Link>
          </DropdownMenuItem>
        )}
        {isSkillAdmin && (
          <DropdownMenuItem asChild>
            <Link to="/dashboard/promotions" className="cursor-pointer">
              {t('user.menu.promotions')}
            </Link>
          </DropdownMenuItem>
        )}
        {isSkillAdmin && (
          <DropdownMenuItem asChild>
            <Link to="/dashboard/reports" className="cursor-pointer">
              {t('user.menu.reports')}
            </Link>
          </DropdownMenuItem>
        )}
        {(isUserAdmin || isAuditor) && <DropdownMenuSeparator />}
        {isUserAdmin && (
          <DropdownMenuItem asChild>
            <Link to="/admin/users" className="cursor-pointer">
              {t('user.menu.users')}
            </Link>
          </DropdownMenuItem>
        )}
        {isAuditor && (
          <DropdownMenuItem asChild>
            <Link to="/admin/audit-log" className="cursor-pointer">
              {t('user.menu.auditLog')}
            </Link>
          </DropdownMenuItem>
        )}
        <DropdownMenuSeparator />
        <DropdownMenuItem asChild>
          <Link to="/settings/security" className="cursor-pointer">
            {t('user.menu.security')}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuItem asChild>
          <Link to="/settings/accounts" className="cursor-pointer">
            {t('user.menu.accounts')}
          </Link>
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem onClick={handleLogout} className="cursor-pointer text-destructive">
          {t('user.menu.logout')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
