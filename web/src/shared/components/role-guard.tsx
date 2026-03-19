import { type ReactNode, useEffect, useRef } from 'react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { canAccessRoute, shouldNavigateBackOnForbidden } from '@/shared/lib/role-guard'
import { toast } from '@/shared/lib/toast'

interface RoleGuardProps {
  allowedRoles: readonly string[]
  children: ReactNode
}

/**
 * Client-side role guard used by protected route components after authentication has resolved.
 */
export function RoleGuard({ allowedRoles, children }: RoleGuardProps) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user, isLoading } = useAuth()
  const hasHandledForbiddenRef = useRef(false)

  const isAllowed = canAccessRoute(user?.platformRoles, allowedRoles)

  useEffect(() => {
    // Only handle the forbidden path once per mount so toasts and redirects do not repeat while the
    // auth query refetches.
    if (isLoading || !user || isAllowed || hasHandledForbiddenRef.current) {
      return
    }

    hasHandledForbiddenRef.current = true
    toast.error(t('routeGuard.forbiddenTitle'), t('routeGuard.forbiddenDescription'))

    if (shouldNavigateBackOnForbidden(window.history.length)) {
      window.history.back()
      return
    }

    void navigate({ to: '/dashboard', replace: true })
  }, [isAllowed, isLoading, navigate, t, user])

  if (isLoading) {
    return (
      <div className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground">
        Loading...
      </div>
    )
  }

  if (!user || !isAllowed) {
    return null
  }

  return <>{children}</>
}
