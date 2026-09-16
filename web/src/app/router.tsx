import { lazy, Suspense, type ComponentType } from 'react'
import { createRouter, createRoute, createRootRoute, redirect } from '@tanstack/react-router'
import { Layout } from './layout'
import { getCurrentUser } from '@/api/client'
import { RoleGuard } from '@/shared/components/role-guard'
import { RouteError } from '@/shared/components/route-error'
import { createRequireAuth } from '@/shared/lib/auth-route'
import { clearDynamicImportReloadGuard, recoverFromDynamicImportError } from '@/shared/lib/dynamic-import-recovery'
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

function RouteLoadingFallback() {
  return (
    <div className="flex min-h-[40vh] items-center justify-center text-sm text-muted-foreground">
      Loading...
    </div>
  )
}

function SilentRouteFallback() {
  return <div className="min-h-[40vh]" aria-hidden />
}

interface LazyRouteOptions {
  silentFallback?: boolean
}

function createLazyRouteComponent<TModule extends Record<string, unknown>>(
  importer: () => Promise<TModule>,
  exportName: keyof TModule,
  options: LazyRouteOptions = {},
) {
  // Most pages keep the visible route fallback. Dashboard-like tab pages can opt into a silent
  // route fallback so their own local data skeleton remains the only loading state users see.
  const LazyComponent = lazy(async () => {
    const module = await importer().catch((error) => {
      if (recoverFromDynamicImportError(error)) {
        return new Promise<never>(() => {})
      }
      throw error
    })
    // Router resolution can finish before React.lazy imports the route module. Only clear the
    // one-time reload guard after the chunk itself has loaded successfully.
    clearDynamicImportReloadGuard()
    return { default: module[exportName] as ComponentType<Record<string, unknown>> }
  })

  return function LazyRouteComponent(props: Record<string, unknown>) {
    return (
      <Suspense fallback={options.silentFallback ? <SilentRouteFallback /> : <RouteLoadingFallback />}>
        <LazyComponent {...props} />
      </Suspense>
    )
  }
}

function createRoleProtectedRouteComponent<TModule extends Record<string, unknown>>(
  importer: () => Promise<TModule>,
  exportName: keyof TModule,
  allowedRoles: readonly string[],
  options: LazyRouteOptions = {},
) {
  // Role checks stay at the route edge so page modules can assume the minimum permission level.
  const RouteComponent = createLazyRouteComponent(importer, exportName, options)

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
const ResetPasswordPage = createLazyRouteComponent(() => import('@/pages/reset-password'), 'ResetPasswordPage')
const PrivacyPolicyPage = createLazyRouteComponent(() => import('@/pages/privacy'), 'PrivacyPolicyPage')
const SearchPage = createLazyRouteComponent(() => import('@/pages/search'), 'SearchPage')
const SuitesPage = createLazyRouteComponent(() => import('@/pages/suites'), 'SuitesPage')
const SuiteDetailPage = createLazyRouteComponent(() => import('@/pages/suite-detail'), 'SuiteDetailPage')
const TermsOfServicePage = createLazyRouteComponent(() => import('@/pages/terms'), 'TermsOfServicePage')
const NamespacePage = createLazyRouteComponent(() => import('@/pages/namespace'), 'NamespacePage')
const SkillDetailPage = createLazyRouteComponent(() => import('@/pages/skill-detail'), 'SkillDetailPage')
const SkillVersionComparePage = createLazyRouteComponent(() => import('@/pages/skill-version-compare'), 'SkillVersionComparePage')
const dashboardRouteOptions = { silentFallback: true } satisfies LazyRouteOptions

const DashboardPage = createLazyRouteComponent(() => import('@/pages/dashboard'), 'DashboardPage', dashboardRouteOptions)
const MySkillsPage = createLazyRouteComponent(() => import('@/pages/dashboard/my-skills'), 'MySkillsPage', dashboardRouteOptions)
const PublishPage = createLazyRouteComponent(() => import('@/pages/dashboard/publish'), 'PublishPage', dashboardRouteOptions)
const SuiteCreatePage = createLazyRouteComponent(
  () => import('@/pages/dashboard/suite-editor'),
  'SuiteCreatePage',
  dashboardRouteOptions,
)
const SuiteEditPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/suite-editor'),
  'SuiteEditPage',
  dashboardRouteOptions,
)
const SuiteVersionCreatePage = createLazyRouteComponent(
  () => import('@/pages/dashboard/suite-editor'),
  'SuiteVersionCreatePage',
  dashboardRouteOptions,
)
const MySuitesPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/my-suites'),
  'MySuitesPage',
  dashboardRouteOptions,
)
const MyNamespacesPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/my-namespaces'),
  'MyNamespacesPage',
  dashboardRouteOptions,
)
const NamespaceMembersPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/namespace-members'),
  'NamespaceMembersPage',
  dashboardRouteOptions,
)
const NamespaceReviewsPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/namespace-reviews'),
  'NamespaceReviewsPage',
  dashboardRouteOptions,
)
const NamespaceReviewDetailPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/review-detail'),
  'NamespaceReviewDetailPage',
  dashboardRouteOptions,
)
const GovernancePage = createLazyRouteComponent(() => import('@/pages/dashboard/governance'), 'GovernancePage', dashboardRouteOptions)
const ReviewsPage = createLazyRouteComponent(() => import('@/pages/dashboard/reviews'), 'ReviewsPage', dashboardRouteOptions)
const ReviewProgressPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/review-progress'),
  'ReviewProgressPage',
  dashboardRouteOptions,
)
const ReportsPage = createRoleProtectedRouteComponent(
  () => import('@/pages/dashboard/reports'),
  'ReportsPage',
  ['SKILL_ADMIN', 'SUPER_ADMIN'],
  dashboardRouteOptions,
)
const ReviewDetailPage = createLazyRouteComponent(() => import('@/pages/dashboard/review-detail'), 'ReviewDetailPage', dashboardRouteOptions)
const PromotionsPage = createRoleProtectedRouteComponent(
  () => import('@/pages/dashboard/promotions'),
  'PromotionsPage',
  ['SKILL_ADMIN', 'SUPER_ADMIN'],
  dashboardRouteOptions,
)
const MyStarsPage = createLazyRouteComponent(() => import('@/pages/dashboard/stars'), 'MyStarsPage', dashboardRouteOptions)
const MySubscriptionsPage = createLazyRouteComponent(() => import('@/pages/dashboard/subscriptions'), 'MySubscriptionsPage', dashboardRouteOptions)
const NotificationsPage = createLazyRouteComponent(() => import('@/pages/notifications'), 'NotificationsPage')
const TokensPage = createLazyRouteComponent(() => import('@/pages/dashboard/tokens'), 'TokensPage', dashboardRouteOptions)
const CliAuthPage = createLazyRouteComponent(() => import('@/pages/cli-auth'), 'CliAuthPage')
const SecuritySettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/security'),
  'SecuritySettingsPage',
  dashboardRouteOptions,
)
const ProfileSettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/profile'),
  'ProfileSettingsPage',
  dashboardRouteOptions,
)
const NotificationSettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/notification-settings'),
  'NotificationSettingsPage',
  dashboardRouteOptions,
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
const AdminNamespacesPage = createRoleProtectedRouteComponent(
  () => import('@/pages/admin/namespaces'),
  'AdminNamespacesPage',
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
  errorComponent: RouteError,
})

const requireAuth = createRequireAuth(getCurrentUser)

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
  validateSearch: (search: Record<string, unknown>): { returnTo?: string; reason?: string } => ({
    returnTo: typeof search.returnTo === 'string' && search.returnTo ? search.returnTo : undefined,
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

const resetPasswordRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'reset-password',
  component: ResetPasswordPage,
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
  validateSearch: (search: Record<string, unknown>): { q: string; namespace?: string; label?: string; sort: string; page: number; starredOnly: boolean } => {
    return {
      q: normalizeSearchQuery(typeof search.q === 'string' ? search.q : ''),
      namespace: typeof search.namespace === 'string' && search.namespace ? search.namespace.replace(/^@/, '') : undefined,
      label: typeof search.label === 'string' && search.label ? search.label : undefined,
      sort: (search.sort as string) || 'newest',
      page: Number(search.page) || 0,
      starredOnly: search.starredOnly === true || search.starredOnly === 'true',
    }
  },
})

const suitesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'suites',
  component: SuitesPage,
})

const suiteDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/suite/$namespace/$slug',
  validateSearch: (search: Record<string, unknown>): { version?: string } => ({
    version: typeof search.version === 'string' && search.version ? search.version : undefined,
  }),
  component: SuiteDetailPage,
})

const termsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'terms',
  component: TermsOfServicePage,
})

const namespaceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/space/$namespace',
  beforeLoad: requireAuth,
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

const skillVersionCompareRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/space/$namespace/$slug/compare',
  validateSearch: (search: Record<string, unknown>): { from: string; to: string } => ({
    from: typeof search.from === 'string' ? search.from : '',
    to: typeof search.to === 'string' ? search.to : '',
  }),
  component: SkillVersionComparePage,
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
  validateSearch: (search: Record<string, unknown>): { page?: number; q?: string; namespace?: string; filter?: string } => ({
    page: typeof search.page === 'number' ? search.page : undefined,
    q: typeof search.q === 'string' && search.q ? search.q : undefined,
    namespace: typeof search.namespace === 'string' && search.namespace ? search.namespace : undefined,
    filter: typeof search.filter === 'string' && search.filter ? search.filter : undefined,
  }),
  component: MySkillsPage,
})

const dashboardPublishRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/publish',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): {
    namespace?: string
    visibility?: string
    resubmitSkill?: string
    resubmitVersion?: string
  } => ({
    namespace: typeof search.namespace === 'string' && search.namespace ? search.namespace : undefined,
    visibility: typeof search.visibility === 'string' && search.visibility ? search.visibility : undefined,
    resubmitSkill: typeof search.resubmitSkill === 'string' && search.resubmitSkill
      ? search.resubmitSkill
      : undefined,
    resubmitVersion: typeof search.resubmitVersion === 'string' && search.resubmitVersion
      ? search.resubmitVersion
      : undefined,
  }),
  component: PublishPage,
})

const dashboardSuiteCreateRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/suites/new',
  beforeLoad: requireAuth,
  component: SuiteCreatePage,
})

const dashboardSuitesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/suites',
  beforeLoad: requireAuth,
  component: MySuitesPage,
})

const dashboardSuiteEditRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/suites/$namespace/$slug/edit',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): { version: string } => ({
    version: typeof search.version === 'string' ? search.version : '',
  }),
  component: SuiteEditPage,
})

const dashboardSuiteVersionCreateRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/suites/$namespace/$slug/new-version',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): { sourceVersion?: string } => ({
    sourceVersion: typeof search.sourceVersion === 'string' && search.sourceVersion
      ? search.sourceVersion
      : undefined,
  }),
  component: SuiteVersionCreatePage,
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
  validateSearch: (search: Record<string, unknown>): { type?: 'skill' | 'profile' } => ({
    type: search.type === 'skill' || search.type === 'profile' ? search.type : undefined,
  }),
  component: ReviewsPage,
})

const dashboardReviewProgressRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/review-progress',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): {
    status?: 'PENDING' | 'APPROVED' | 'REJECTED'
    q?: string
    page?: number
  } => ({
    status: search.status === 'PENDING' || search.status === 'APPROVED' || search.status === 'REJECTED'
      ? search.status
      : undefined,
    q: typeof search.q === 'string' && search.q.trim() ? search.q.trim() : undefined,
    page: typeof search.page === 'number' && search.page > 0 ? search.page : undefined,
  }),
  component: ReviewProgressPage,
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

const dashboardNamespaceReviewDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/namespaces/$slug/reviews/$id',
  beforeLoad: requireAuth,
  component: NamespaceReviewDetailPage,
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
  validateSearch: (search: Record<string, unknown>): { page?: number } => ({
    page: typeof search.page === 'number' && search.page > 0 ? search.page : undefined,
  }),
  component: MyStarsPage,
})

const dashboardSubscriptionsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/subscriptions',
  beforeLoad: requireAuth,
  validateSearch: (search: Record<string, unknown>): { page?: number } => ({
    page: typeof search.page === 'number' && search.page > 0 ? search.page : undefined,
  }),
  component: MySubscriptionsPage,
})

const dashboardNotificationsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'dashboard/notifications',
  beforeLoad: requireAuth,
  component: NotificationsPage,
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

const settingsNotificationsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'settings/notifications',
  beforeLoad: requireAuth,
  component: NotificationSettingsPage,
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

const adminNamespacesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'admin/namespaces',
  beforeLoad: requireAuth,
  component: AdminNamespacesPage,
})

const routeTree = rootRoute.addChildren([
  landingRoute,
  skillsRoute,
  loginRoute,
  registerRoute,
  resetPasswordRoute,
  privacyRoute,
  searchRoute,
  suitesRoute,
  suiteDetailRoute,
  termsRoute,
  namespaceRoute,
  skillDetailRoute,
  skillVersionCompareRoute,
  dashboardRoute,
  dashboardSkillsRoute,
  dashboardPublishRoute,
  dashboardSuitesRoute,
  dashboardSuiteCreateRoute,
  dashboardSuiteEditRoute,
  dashboardSuiteVersionCreateRoute,
  dashboardNamespacesRoute,
  dashboardNamespaceMembersRoute,
  dashboardNamespaceReviewsRoute,
  dashboardNamespaceReviewDetailRoute,
  dashboardGovernanceRoute,
  dashboardReviewsRoute,
  dashboardReviewProgressRoute,
  dashboardReportsRoute,
  dashboardReviewDetailRoute,
  dashboardPromotionsRoute,
  dashboardStarsRoute,
  dashboardSubscriptionsRoute,
  dashboardNotificationsRoute,
  dashboardTokensRoute,
  cliAuthRoute,
  settingsSecurityRoute,
  settingsProfileRoute,
  settingsNotificationsRoute,
  settingsAccountsRoute,
  adminUsersRoute,
  adminAuditLogRoute,
  adminLabelsRoute,
  adminNamespacesRoute,
])

export const router = createRouter({
  routeTree,
  basepath: import.meta.env.BASE_URL,
  defaultNotFoundComponent: DefaultNotFound,
  defaultErrorComponent: RouteError,
})

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
