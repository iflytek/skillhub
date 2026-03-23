import { lazy, Suspense, type ComponentType } from 'react'
import { createRouter, createRoute, createRootRoute, redirect } from '@tanstack/react-router'
import { Layout } from './layout'
import { getCurrentUser } from '@/api/client'
import { RoleGuard } from '@/shared/components/role-guard'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

/**
 * Central route registry for the SkillHub web app.
 *
 * This file keeps route declarations, auth redirects, role-based wrappers, and search-param
 * normalization in one place so route behavior remains explicit.
 */
// Capture original URL before TanStack Router rewrites it
const ORIGINAL_URL_SEARCH = typeof window !== 'undefined' ? window.location.search : ''

// Export for use in cli-auth page
export { ORIGINAL_URL_SEARCH }

function createLazyRouteComponent<TModule extends Record<string, unknown>>(
  importer: () => Promise<TModule>,
  exportName: keyof TModule,
) {
  // Lazy route modules are wrapped in a uniform suspense fallback so route transitions behave
  // consistently across public and dashboard pages.
  const LazyComponent = lazy(async () => {
    const module = await importer()
    return { default: module[exportName] as ComponentType<Record<string, unknown>> }
  })

  return function LazyRouteComponent(props: Record<string, unknown>) {
    return (
      <Suspense
        fallback={
          <div className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground">
            Loading...
          </div>
        }
      >
        <LazyComponent {...props} />
      </Suspense>
    )
  }
}

function createRoleProtectedRouteComponent<TModule extends Record<string, unknown>>(
  importer: () => Promise<TModule>,
  exportName: keyof TModule,
  allowedRoles: readonly string[],
) {
  // Role checks stay at the route edge so page modules can assume the minimum permission level.
  const RouteComponent = createLazyRouteComponent(importer, exportName)

  return function RoleProtectedRouteComponent(props: Record<string, unknown>) {
    return (
      <RoleGuard allowedRoles={allowedRoles}>
        <RouteComponent {...props} />
      </RoleGuard>
    )
  }
}

const LandingPage = createLazyRouteComponent(() => import('@/pages/landing'), 'LandingPage')
const HomePage = createLazyRouteComponent(() => import('@/pages/home'), 'HomePage')
const LoginPage = createLazyRouteComponent(() => import('@/pages/login'), 'LoginPage')
const RegisterPage = createLazyRouteComponent(() => import('@/pages/register'), 'RegisterPage')
const PrivacyPolicyPage = createLazyRouteComponent(() => import('@/pages/privacy'), 'PrivacyPolicyPage')
const SearchPage = createLazyRouteComponent(() => import('@/pages/search'), 'SearchPage')
const TermsOfServicePage = createLazyRouteComponent(() => import('@/pages/terms'), 'TermsOfServicePage')
const NamespacePage = createLazyRouteComponent(() => import('@/pages/namespace'), 'NamespacePage')
const SkillDetailPage = createLazyRouteComponent(() => import('@/pages/skill-detail'), 'SkillDetailPage')
const DashboardPage = createLazyRouteComponent(() => import('@/pages/dashboard'), 'DashboardPage')
const MySkillsPage = createLazyRouteComponent(() => import('@/pages/dashboard/my-skills'), 'MySkillsPage')
const PublishPage = createLazyRouteComponent(() => import('@/pages/dashboard/publish'), 'PublishPage')
const MyNamespacesPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/my-namespaces'),
  'MyNamespacesPage',
)
const NamespaceMembersPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/namespace-members'),
  'NamespaceMembersPage',
)
const NamespaceReviewsPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/namespace-reviews'),
  'NamespaceReviewsPage',
)
const GovernancePage = createLazyRouteComponent(() => import('@/pages/dashboard/governance'), 'GovernancePage')
const ReviewsPage = createRoleProtectedRouteComponent(
  () => import('@/pages/dashboard/reviews'),
  'ReviewsPage',
  ['SKILL_ADMIN', 'NAMESPACE_ADMIN', 'USER_ADMIN', 'SUPER_ADMIN'],
)
const ReportsPage = createRoleProtectedRouteComponent(
  () => import('@/pages/dashboard/reports'),
  'ReportsPage',
  ['SKILL_ADMIN', 'SUPER_ADMIN'],
)
const ReviewDetailPage = createRoleProtectedRouteComponent(
  () => import('@/pages/dashboard/review-detail'),
  'ReviewDetailPage',
  ['SKILL_ADMIN', 'NAMESPACE_ADMIN', 'SUPER_ADMIN'],
)
const PromotionsPage = createRoleProtectedRouteComponent(
  () => import('@/pages/dashboard/promotions'),
  'PromotionsPage',
  ['SKILL_ADMIN', 'SUPER_ADMIN'],
)
const MyStarsPage = createLazyRouteComponent(() => import('@/pages/dashboard/stars'), 'MyStarsPage')
const TokensPage = createLazyRouteComponent(() => import('@/pages/dashboard/tokens'), 'TokensPage')
const CliAuthPage = createLazyRouteComponent(() => import('@/pages/cli-auth'), 'CliAuthPage')
const SecuritySettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/security'),
  'SecuritySettingsPage',
)
const ProfileSettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/profile'),
  'ProfileSettingsPage',
)
const AdminUsersPage = createRoleProtectedRouteComponent(
  () => import('@/pages/admin/users'),
  'AdminUsersPage',
  ['USER_ADMIN', 'SUPER_ADMIN'],
)
const AuditLogPage = createRoleProtectedRouteComponent(
  () => import('@/pages/admin/audit-log'),
  'AuditLogPage',
  ['AUDITOR', 'SUPER_ADMIN'],
)
const AdminLabelsPage = createRoleProtectedRouteComponent(
  () => import('@/pages/admin/labels'),
  'AdminLabelsPage',
  ['SUPER_ADMIN'],
)

function DefaultNotFound() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground">
      Not Found
    </div>
  )
}

const rootRoute = createRootRoute({
  component: Layout,
  notFoundComponent: DefaultNotFound,
})

function buildReturnTo(location: { pathname: string; searchStr?: string; hash?: string }) {
  return `${location.pathname}${location.searchStr ?? ''}${location.hash ?? ''}`
}

async function requireAuth({ location }: { location: { pathname: string; searchStr?: string; hash?: string } }) {
  // Resolve the current session before entering protected areas and preserve the full return URL.
  const user = await getCurrentUser()
  if (!user) {
    throw redirect({
      to: '/login',
      search: { returnTo: buildReturnTo(location) },
    })
  }
  return { user }
}

const landingRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LandingPage,
})

const skillsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'skills',
  component: HomePage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'login',
  validateSearch: (search: Record<string, unknown>): { returnTo: string; reason?: string } => ({
    returnTo: typeof search.returnTo === 'string' ? search.returnTo : '',
    reason: typeof search.reason === 'string' ? search.reason : undefined,
  }),
  component: LoginPage,
})

const registerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'register',
  validateSearch: (search: Record<string, unknown>) => ({
    returnTo: typeof search.returnTo === 'string' ? search.returnTo : '',
  }),
  component: RegisterPage,
})

const privacyRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'privacy',
  component: PrivacyPolicyPage,
})

const searchRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'search',
  component: SearchPage,
  validateSearch: (search: Record<string, unknown>): { q: string; label?: string; sort: string; page: number; starredOnly: boolean } => {
    return {
      q: normalizeSearchQuery(typeof search.q === 'string' ? search.q : ''),
      label: typeof search.label === 'string' && search.label ? search.label : undefined,
      sort: (search.sort as string) || 'newest',
      page: Number(search.page) || 0,
      starredOnly: search.starredOnly === true || search.starredOnly === 'true',
    }
  },
})

const termsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'terms',
  component: TermsOfServicePage,
})

const namespaceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/space/$namespace',
  component: NamespacePage,
})

const skillDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/space/$namespace/$slug',
  validateSearch: (search: Record<string, unknown>): { returnTo?: string } => ({
    returnTo: typeof search.returnTo === 'string' && search.returnTo.startsWith('/') ? search.returnTo : undefined,
  }),
  component: SkillDetailPage,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard',
  beforeLoad: requireAuth,
  component: DashboardPage,
})

const dashboardSkillsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/skills',
  beforeLoad: requireAuth,
  component: MySkillsPage,
})

const dashboardPublishRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/publish',
  beforeLoad: requireAuth,
  component: PublishPage,
})

const dashboardNamespacesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/namespaces',
  beforeLoad: requireAuth,
  component: MyNamespacesPage,
})

const dashboardNamespaceMembersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/namespaces/$slug/members',
  beforeLoad: requireAuth,
  component: NamespaceMembersPage,
})

const dashboardNamespaceReviewsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/namespaces/$slug/reviews',
  beforeLoad: requireAuth,
  component: NamespaceReviewsPage,
})

const dashboardGovernanceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/governance',
  beforeLoad: requireAuth,
  component: GovernancePage,
})

const dashboardReviewsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/reviews',
  beforeLoad: requireAuth,
  component: ReviewsPage,
})

const dashboardReportsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/reports',
  beforeLoad: requireAuth,
  component: ReportsPage,
})

const dashboardReviewDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/reviews/$id',
  beforeLoad: requireAuth,
  component: ReviewDetailPage,
})

const dashboardPromotionsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/promotions',
  beforeLoad: requireAuth,
  component: PromotionsPage,
})

const dashboardStarsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/stars',
  beforeLoad: requireAuth,
  component: MyStarsPage,
})

const dashboardTokensRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/tokens',
  beforeLoad: requireAuth,
  component: TokensPage,
})

const cliAuthRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'cli/auth',
  component: CliAuthPage,
  validateSearch: (search: Record<string, unknown>): Record<string, string> => {
    // Preserve all CLI auth parameters - use empty string instead of undefined to prevent TanStack Router from removing them
    return {
      redirect_uri: typeof search.redirect_uri === 'string' ? search.redirect_uri : '',
      label_b64: typeof search.label_b64 === 'string' ? search.label_b64 : '',
      label: typeof search.label === 'string' ? search.label : '',
      state: typeof search.state === 'string' ? search.state : '',
    }
  },
})

const settingsSecurityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'settings/security',
  beforeLoad: requireAuth,
  component: SecuritySettingsPage,
})

const settingsProfileRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'settings/profile',
  beforeLoad: requireAuth,
  component: ProfileSettingsPage,
})

const settingsAccountsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'settings/accounts',
  beforeLoad: async (ctx) => {
    await requireAuth(ctx)
    throw redirect({ to: '/settings/security' })
  },
})

const adminUsersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'admin/users',
  beforeLoad: requireAuth,
  component: AdminUsersPage,
})

const adminAuditLogRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'admin/audit-log',
  beforeLoad: requireAuth,
  component: AuditLogPage,
})

const adminLabelsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'admin/labels',
  beforeLoad: requireAuth,
  component: AdminLabelsPage,
})

const routeTree = rootRoute.addChildren([
  landingRoute,
  skillsRoute,
  loginRoute,
  registerRoute,
  privacyRoute,
  searchRoute,
  termsRoute,
  namespaceRoute,
  skillDetailRoute,
  dashboardRoute,
  dashboardSkillsRoute,
  dashboardPublishRoute,
  dashboardNamespacesRoute,
  dashboardNamespaceMembersRoute,
  dashboardNamespaceReviewsRoute,
  dashboardGovernanceRoute,
  dashboardReviewsRoute,
  dashboardReportsRoute,
  dashboardReviewDetailRoute,
  dashboardPromotionsRoute,
  dashboardStarsRoute,
  dashboardTokensRoute,
  cliAuthRoute,
  settingsSecurityRoute,
  settingsProfileRoute,
  settingsAccountsRoute,
  adminUsersRoute,
  adminAuditLogRoute,
  adminLabelsRoute,
])

export const router = createRouter({
  routeTree,
  defaultNotFoundComponent: DefaultNotFound,
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
