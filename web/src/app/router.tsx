import { lazy, Suspense, type ComponentType } from 'react'
import { createRouter, createRoute, createRootRoute, redirect } from '@tanstack/react-router'
import { Layout } from './layout'
import { getCurrentUser } from '@/api/client'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

// Capture original URL before TanStack Router rewrites it
const ORIGINAL_URL_SEARCH = typeof window !== 'undefined' ? window.location.search : ''

// Export for use in cli-auth page
export { ORIGINAL_URL_SEARCH }

function createLazyRouteComponent<TModule extends Record<string, unknown>>(
  importer: () => Promise<TModule>,
  exportName: keyof TModule,
) {
  const LazyComponent = lazy(async () => {
    const module = await importer()
    return { default: module[exportName] as ComponentType<any> }
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
const ReviewsPage = createLazyRouteComponent(() => import('@/pages/dashboard/reviews'), 'ReviewsPage')
const ReportsPage = createLazyRouteComponent(() => import('@/pages/dashboard/reports'), 'ReportsPage')
const ReviewDetailPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/review-detail'),
  'ReviewDetailPage',
)
const PromotionsPage = createLazyRouteComponent(
  () => import('@/pages/dashboard/promotions'),
  'PromotionsPage',
)
const MyStarsPage = createLazyRouteComponent(() => import('@/pages/dashboard/stars'), 'MyStarsPage')
const TokensPage = createLazyRouteComponent(() => import('@/pages/dashboard/tokens'), 'TokensPage')
const CliAuthPage = createLazyRouteComponent(() => import('@/pages/cli-auth'), 'CliAuthPage')
const SecuritySettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/security'),
  'SecuritySettingsPage',
)
const AccountSettingsPage = createLazyRouteComponent(
  () => import('@/pages/settings/accounts'),
  'AccountSettingsPage',
)
const AdminUsersPage = createLazyRouteComponent(() => import('@/pages/admin/users'), 'AdminUsersPage')
const AuditLogPage = createLazyRouteComponent(() => import('@/pages/admin/audit-log'), 'AuditLogPage')

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
  const user = await getCurrentUser()
  if (!user) {
    throw redirect({
      to: '/login',
      search: { returnTo: buildReturnTo(location) },
    })
  }
  return { user }
}

const skillsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'skills',
  component: HomePage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'login',
  validateSearch: (search: Record<string, unknown>) => ({
    returnTo: typeof search.returnTo === 'string' ? search.returnTo : '',
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
  validateSearch: (search: Record<string, unknown>) => {
    return {
      q: normalizeSearchQuery(typeof search.q === 'string' ? search.q : ''),
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
  beforeLoad: async (ctx) => {
    const { user } = await requireAuth(ctx)
    if (!user.platformRoles?.includes('SKILL_ADMIN') && !user.platformRoles?.includes('SUPER_ADMIN')) {
      throw redirect({ to: '/dashboard' })
    }
    return { user }
  },
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
  beforeLoad: async (ctx) => {
    const { user } = await requireAuth(ctx)
    if (!user.platformRoles?.includes('SKILL_ADMIN') && !user.platformRoles?.includes('SUPER_ADMIN')) {
      throw redirect({ to: '/dashboard' })
    }
    return { user }
  },
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

const settingsAccountsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'settings/accounts',
  beforeLoad: requireAuth,
  component: AccountSettingsPage,
})

const adminUsersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'admin/users',
  beforeLoad: async (ctx) => {
    const { user } = await requireAuth(ctx)
    if (!user.platformRoles?.includes('USER_ADMIN') && !user.platformRoles?.includes('SUPER_ADMIN')) {
      throw redirect({ to: '/dashboard' })
    }
    return { user }
  },
  component: AdminUsersPage,
})

const adminAuditLogRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'admin/audit-log',
  beforeLoad: async (ctx) => {
    const { user } = await requireAuth(ctx)
    if (!user.platformRoles?.includes('AUDITOR') && !user.platformRoles?.includes('SUPER_ADMIN')) {
      throw redirect({ to: '/dashboard' })
    }
    return { user }
  },
  component: AuditLogPage,
})

const routeTree = rootRoute.addChildren([
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
  settingsAccountsRoute,
  adminUsersRoute,
  adminAuditLogRoute,
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
