import { useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { useQueryClient } from '@tanstack/react-query'
import { authApi } from '@/api/client'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { buildGlobalReviewsPath, canAccessReviewCenter } from '@/features/review/review-paths'
import { clearSessionScopedQueries } from '@/features/notification/notification-session'
import { canViewGovernanceCenter } from '@/shared/lib/governance-access'
import { cn } from '@/shared/lib/utils'

interface User {
  displayName: string
  avatarUrl?: string
  platformRoles?: string[]
  oauthProvider?: string
}

interface UserMenuProps {
  user: User
  triggerClassName?: string
}

export function UserMenu({ user, triggerClassName }: UserMenuProps) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const { data: myNamespaces } = useMyNamespaces()
  const rootRef = useRef<HTMLDivElement | null>(null)
  const closeTimerRef = useRef<number | null>(null)
  const [isHovered, setIsHovered] = useState(false)
  const [isClickOpen, setIsClickOpen] = useState(false)

  const hasRole = (role: string) => user.platformRoles?.includes(role) ?? false
  const canSeeGovernance = canViewGovernanceCenter(user.platformRoles)
  const isSkillAdmin = hasRole('SKILL_ADMIN') || hasRole('SUPER_ADMIN')
  const isUserAdmin = hasRole('USER_ADMIN') || hasRole('SUPER_ADMIN')
  const isAuditor = hasRole('AUDITOR') || hasRole('SUPER_ADMIN')
  const isSuperAdmin = hasRole('SUPER_ADMIN')
  const reviewCenterVisible = canAccessReviewCenter(user.platformRoles, myNamespaces)
  const isLocalAccount = !user.oauthProvider
  const open = isHovered || isClickOpen

  const clearCloseTimer = () => {
    if (closeTimerRef.current !== null) {
      window.clearTimeout(closeTimerRef.current)
      closeTimerRef.current = null
    }
  }

  useEffect(() => {
    if (!open) {
      return
    }

    const handlePointerDown = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) {
        setIsHovered(false)
        setIsClickOpen(false)
      }
    }

    document.addEventListener('mousedown', handlePointerDown)
    return () => {
      document.removeEventListener('mousedown', handlePointerDown)
    }
  }, [open])

  useEffect(() => {
    return () => {
      clearCloseTimer()
    }
  }, [])

  const handleLogout = async () => {
    try {
      await authApi.logout()
    } catch (error) {
      console.error('Logout failed:', error)
    } finally {
      // Always clear cache and redirect, even if API call fails
      clearSessionScopedQueries(queryClient)
      queryClient.setQueryData(['auth', 'me'], null)
      window.location.href = '/'
    }
  }

  const closeMenu = () => {
    clearCloseTimer()
    setIsHovered(false)
    setIsClickOpen(false)
  }

  const handleMouseEnter = () => {
    clearCloseTimer()
    setIsHovered(true)
  }

  const handleMouseLeave = () => {
    clearCloseTimer()
    closeTimerRef.current = window.setTimeout(() => {
      setIsHovered(false)
      closeTimerRef.current = null
    }, 120)
  }

  const menuItemClassName =
    'block w-full rounded-sm px-2 py-1.5 text-sm transition-colors hover:bg-accent hover:text-accent-foreground'

  return (
    <div
      ref={rootRef}
      className="relative"
      onMouseEnter={handleMouseEnter}
      onMouseLeave={handleMouseLeave}
    >
      <button
        type="button"
        aria-expanded={open}
        aria-haspopup="menu"
        className={cn('flex items-center gap-3 text-foreground hover:opacity-80 transition-opacity focus:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:rounded-md', triggerClassName)}
        onClick={() => setIsClickOpen((current) => !current)}
      >
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
      {open ? (
        <div
          className="absolute right-0 top-full z-50 w-48 pt-2"
          onMouseEnter={handleMouseEnter}
          onMouseLeave={handleMouseLeave}
        >
          <div
            role="menu"
            className="overflow-hidden rounded-md border bg-popover p-1 text-popover-foreground shadow-md"
          >
            <Link to="/dashboard" className={menuItemClassName} onClick={closeMenu}>
              {t('user.menu.dashboard')}
            </Link>
            <Link to="/dashboard/skills" className={menuItemClassName} onClick={closeMenu}>
              {t('user.menu.mySkills')}
            </Link>
            <Link to="/dashboard/namespaces" className={menuItemClassName} onClick={closeMenu}>
              {t('user.menu.myNamespaces')}
            </Link>
            {canSeeGovernance ? (
              <Link to="/dashboard/governance" className={menuItemClassName} onClick={closeMenu}>
                {t('user.menu.governance')}
              </Link>
            ) : null}
            <Link to="/dashboard/stars" className={menuItemClassName} onClick={closeMenu}>
              {t('user.menu.stars')}
            </Link>
            {reviewCenterVisible ? (
              <Link to={buildGlobalReviewsPath()} className={menuItemClassName} onClick={closeMenu}>
                {t('user.menu.reviews')}
              </Link>
            ) : null}
            {isSkillAdmin ? (
              <Link to="/dashboard/promotions" className={menuItemClassName} onClick={closeMenu}>
                {t('user.menu.promotions')}
              </Link>
            ) : null}
            {isSkillAdmin ? (
              <Link to="/dashboard/reports" className={menuItemClassName} onClick={closeMenu}>
                {t('user.menu.reports')}
              </Link>
            ) : null}
            {isUserAdmin || isAuditor || isSuperAdmin ? <div className="-mx-1 my-1 h-px bg-muted" /> : null}
            {isUserAdmin ? (
              <Link to="/admin/users" className={menuItemClassName} onClick={closeMenu}>
                {t('user.menu.users')}
              </Link>
            ) : null}
            {isSuperAdmin ? (
              <Link to="/admin/labels" className={menuItemClassName} onClick={closeMenu}>
                {t('user.menu.labels')}
              </Link>
            ) : null}
            {isAuditor ? (
              <Link to="/admin/audit-log" className={menuItemClassName} onClick={closeMenu}>
                {t('user.menu.auditLog')}
              </Link>
            ) : null}
            <div className="-mx-1 my-1 h-px bg-muted" />
            <Link to="/settings/profile" className={menuItemClassName} onClick={closeMenu}>
              {t('user.menu.profile')}
            </Link>
            <Link to="/settings/notifications" className={menuItemClassName} onClick={closeMenu}>
              {t('user.menu.notifications')}
            </Link>
            {isLocalAccount ? (
              <Link to="/settings/security" className={menuItemClassName} onClick={closeMenu}>
                {t('user.menu.security')}
              </Link>
            ) : null}
            <div className="-mx-1 my-1 h-px bg-muted" />
            <button
              type="button"
              onClick={handleLogout}
              className={cn(menuItemClassName, 'text-destructive')}
            >
              {t('user.menu.logout')}
            </button>
          </div>
        </div>
      ) : null}
    </div>
  )
}
