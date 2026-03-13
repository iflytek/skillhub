import { lazy, type ComponentType } from 'react'
import { createRouter, createRoute, createRootRoute, redirect } from '@tanstack/react-router'
import { Layout } from './layout'
import { getCurrentUser } from '@/api/client'

function lazyRouteComponent<TModule extends Record<string, unknown>>(
  importer: () => Promise<TModule>,
  exportName: keyof TModule,
) {
  const LazyComponent = lazy(async () => {
    const module = await importer()
    return { default: module[exportName] as ComponentType }
  })

  return LazyComponent
}

const LandingPage = lazyRouteComponent(() => import('@/pages/landing'), 'LandingPage')
const HomePage = lazyRouteComponent(() => import('@/pages/home'), 'HomePage')
const LoginPage = lazyRouteComponent(() => import('@/pages/login'), 'LoginPage')
const RegisterPage = lazyRouteComponent(() => import('@/pages/register'), 'RegisterPage')
const SearchPage = lazyRouteComponent(() => import('@/pages/search'), 'SearchPage')
const NamespacePage = lazyRouteComponent(() => import('@/pages/namespace'), 'NamespacePage')
const SkillDetailPage = lazyRouteComponent(() => import('@/pages/skill-detail'), 'SkillDetailPage')
const DashboardPage = lazyRouteComponent(() => import('@/pages/dashboard'), 'DashboardPage')
const MySkillsPage = lazyRouteComponent(() => import('@/pages/dashboard/my-skills'), 'MySkillsPage')
const PublishPage = lazyRouteComponent(() => import('@/pages/dashboard/publish'), 'PublishPage')
const MyNamespacesPage = lazyRouteComponent(() => import('@/pages/dashboard/my-namespaces'), 'MyNamespacesPage')
const NamespaceMembersPage = lazyRouteComponent(() => import('@/pages/dashboard/namespace-members'), 'NamespaceMembersPage')
const NamespaceReviewsPage = lazyRouteComponent(() => import('@/pages/dashboard/namespace-reviews'), 'NamespaceReviewsPage')
const ReviewsPage = lazyRouteComponent(() => import('@/pages/dashboard/reviews'), 'ReviewsPage')
const ReviewDetailPage = lazyRouteComponent(() => import('@/pages/dashboard/review-detail'), 'ReviewDetailPage')
const PromotionsPage = lazyRouteComponent(() => import('@/pages/dashboard/promotions'), 'PromotionsPage')
const MyStarsPage = lazyRouteComponent(() => import('@/pages/dashboard/stars'), 'MyStarsPage')
const TokensPage = lazyRouteComponent(() => import('@/pages/dashboard/tokens'), 'TokensPage')
const DeviceAuthPage = lazyRouteComponent(() => import('@/pages/device'), 'DeviceAuthPage')
const SecuritySettingsPage = lazyRouteComponent(() => import('@/pages/settings/security'), 'SecuritySettingsPage')
const AccountSettingsPage = lazyRouteComponent(() => import('@/pages/settings/accounts'), 'AccountSettingsPage')
const AdminUsersPage = lazyRouteComponent(() => import('@/pages/admin/users'), 'AdminUsersPage')
const AuditLogPage = lazyRouteComponent(() => import('@/pages/admin/audit-log'), 'AuditLogPage')

const rootRoute = createRootRoute({
  component: Layout,
})

const homeRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/',
  component: LandingPage,
})

const skillsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/skills',
  component: HomePage,
})

const loginRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/login',
  component: LoginPage,
})

const registerRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/register',
  component: RegisterPage,
})

const searchRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/search',
  component: SearchPage,
  validateSearch: (search: Record<string, unknown>) => {
    return {
      q: (search.q as string) || '',
      sort: (search.sort as string) || 'relevance',
      page: Number(search.page) || 1,
    }
  },
})

const namespaceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/@$namespace',
  component: NamespacePage,
})

const skillDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/@$namespace/$slug',
  component: SkillDetailPage,
})

const dashboardRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: DashboardPage,
})

const dashboardSkillsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/skills',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: MySkillsPage,
})

const dashboardPublishRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/publish',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: PublishPage,
})

const dashboardNamespacesRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/namespaces',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: MyNamespacesPage,
})

const dashboardNamespaceMembersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/namespaces/$slug/members',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: NamespaceMembersPage,
})

const dashboardNamespaceReviewsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/namespaces/$slug/reviews',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: NamespaceReviewsPage,
})

const dashboardReviewsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/reviews',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: ReviewsPage,
})

const dashboardReviewDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/reviews/$id',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: ReviewDetailPage,
})

const dashboardPromotionsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/promotions',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    if (!user.platformRoles?.includes('SKILL_ADMIN') && !user.platformRoles?.includes('SUPER_ADMIN')) {
      throw redirect({ to: '/dashboard' })
    }
    return { user }
  },
  component: PromotionsPage,
})

const dashboardStarsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/stars',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: MyStarsPage,
})

const dashboardTokensRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/dashboard/tokens',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: TokensPage,
})

const deviceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/device',
  component: DeviceAuthPage,
})

const settingsSecurityRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings/security',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: SecuritySettingsPage,
})

const settingsAccountsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/settings/accounts',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    return { user }
  },
  component: AccountSettingsPage,
})

const adminUsersRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/admin/users',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    if (!user.platformRoles?.includes('USER_ADMIN') && !user.platformRoles?.includes('SUPER_ADMIN')) {
      throw redirect({ to: '/dashboard' })
    }
    return { user }
  },
  component: AdminUsersPage,
})

const adminAuditLogRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/admin/audit-log',
  beforeLoad: async () => {
    const user = await getCurrentUser()
    if (!user) {
      throw redirect({ to: '/login' })
    }
    if (!user.platformRoles?.includes('AUDITOR') && !user.platformRoles?.includes('SUPER_ADMIN')) {
      throw redirect({ to: '/dashboard' })
    }
    return { user }
  },
  component: AuditLogPage,
})

const routeTree = rootRoute.addChildren([
  homeRoute,
  skillsRoute,
  loginRoute,
  registerRoute,
  searchRoute,
  namespaceRoute,
  skillDetailRoute,
  dashboardRoute,
  dashboardSkillsRoute,
  dashboardPublishRoute,
  dashboardNamespacesRoute,
  dashboardNamespaceMembersRoute,
  dashboardNamespaceReviewsRoute,
  dashboardReviewsRoute,
  dashboardReviewDetailRoute,
  dashboardPromotionsRoute,
  dashboardStarsRoute,
  dashboardTokensRoute,
  deviceRoute,
  settingsSecurityRoute,
  settingsAccountsRoute,
  adminUsersRoute,
  adminAuditLogRoute,
])

export const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
