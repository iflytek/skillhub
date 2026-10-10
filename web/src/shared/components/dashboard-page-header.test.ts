import { describe, expect, it } from 'vitest'
import * as mod from './dashboard-page-header'

/**
 * DashboardPageHeader is a React component that renders a standard header for
 * dashboard sub-pages with a back button, title, subtitle, and action slot.
 * All logic depends on useNavigate and useTranslation hooks.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('dashboard-page-header module exports', () => {
  it('exports the DashboardPageHeader component', () => {
    expect(mod.DashboardPageHeader).toBeTypeOf('function')
  })
})
