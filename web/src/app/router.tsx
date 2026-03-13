import { createRouter, createRoute, createRootRoute, redirect } from '@tanstack/react-router'
import { Layout } from './layout'
import { LandingPage } from '@/pages/landing'
import { HomePage } from '@/pages/home'
import { LoginPage } from '@/pages/login'
import { DashboardPage } from '@/pages/dashboard'
import { SearchPage } from '@/pages/search'
import { NamespacePage } from '@/pages/namespace'
import { SkillDetailPage } from '@/pages/skill-detail'
import { PublishPage } from '@/pages/dashboard/publish'
import { MySkillsPage } from '@/pages/dashboard/my-skills'
import { MyNamespacesPage } from '@/pages/dashboard/my-namespaces'
import { NamespaceMembersPage } from '@/pages/dashboard/namespace-members'
import { ReviewsPage } from '@/pages/dashboard/reviews'
import { ReviewDetailPage } from '@/pages/dashboard/review-detail'
import { DeviceAuthPage } from '@/pages/device'
import { AdminUsersPage } from '@/pages/admin/users'
import { AuditLogPage } from '@/pages/admin/audit-log'
import { getCurrentUser } from '@/api/client'

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

const deviceRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: '/device',
  component: DeviceAuthPage,
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
  searchRoute,
  namespaceRoute,
  skillDetailRoute,
  dashboardRoute,
  dashboardSkillsRoute,
  dashboardPublishRoute,
  dashboardNamespacesRoute,
  dashboardNamespaceMembersRoute,
  dashboardReviewsRoute,
  dashboardReviewDetailRoute,
  deviceRoute,
  adminUsersRoute,
  adminAuditLogRoute,
])

export const router = createRouter({ routeTree })

declare module '@tanstack/react-router' {
  interface Register {
    router: typeof router
  }
}
