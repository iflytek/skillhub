import { describe, expect, it } from 'vitest'
import * as mod from './role-guard'

/**
 * RoleGuard is a React component that enforces client-side role-based access.
 * It delegates to pure helpers canAccessRoute() and shouldNavigateBackOnForbidden()
 * from @/shared/lib/role-guard, which are already tested in shared/lib/role-guard.test.ts.
 * The component itself depends on useAuth, useNavigate, and useTranslation hooks.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('role-guard component module exports', () => {
  it('exports the RoleGuard component', () => {
    expect(mod.RoleGuard).toBeTypeOf('function')
  })
})
