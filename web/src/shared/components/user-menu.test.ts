import { describe, expect, it } from 'vitest'
import * as mod from './user-menu'

/**
 * UserMenu is a React component that renders a hover/click dropdown menu with
 * role-based navigation links (dashboard, reviews, admin, etc.) and logout.
 * Internal helpers (hasRole, closeMenu, handleMouseEnter/Leave) and the
 * menuItemClassName constant are scoped inside the component function.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('user-menu module exports', () => {
  it('exports the UserMenu component', () => {
    expect(mod.UserMenu).toBeTypeOf('function')
  })
})
