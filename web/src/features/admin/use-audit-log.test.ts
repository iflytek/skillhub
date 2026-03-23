import { describe, expect, it } from 'vitest'
import * as auditLog from './use-audit-log'

/**
 * use-audit-log exports interfaces (AuditLogParams, PagedAuditLogs) for typing
 * and a single useQuery hook (useAuditLog) that delegates to adminApi.getAuditLogs.
 * The query key includes the full params object for cache isolation.
 *
 * There are no exported pure functions, constants, or data transformations to
 * unit-test. This file verifies the public API surface.
 */
describe('use-audit-log module exports', () => {
  it('exports useAuditLog query hook', () => {
    expect(auditLog.useAuditLog).toBeTypeOf('function')
  })
})
