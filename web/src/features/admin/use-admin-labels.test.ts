import { describe, expect, it } from 'vitest'
import * as adminLabels from './use-admin-labels'

/**
 * use-admin-labels exports four thin useMutation hooks (useCreateAdminLabel,
 * useUpdateAdminLabel, useDeleteAdminLabel, useUpdateAdminLabelSortOrder) that
 * delegate to labelApi. Each invalidates ['labels'] and ['skills'] query caches
 * on success. It also re-exports useAdminLabelDefinitions from the shared hooks
 * module.
 *
 * There are no exported pure functions, constants, or data transformations to
 * unit-test. This file verifies the public API surface.
 */
describe('use-admin-labels module exports', () => {
  it('exports useAdminLabelDefinitions re-exported from shared hooks', () => {
    expect(adminLabels.useAdminLabelDefinitions).toBeTypeOf('function')
  })

  it('exports useCreateAdminLabel hook', () => {
    expect(adminLabels.useCreateAdminLabel).toBeTypeOf('function')
  })

  it('exports useUpdateAdminLabel hook', () => {
    expect(adminLabels.useUpdateAdminLabel).toBeTypeOf('function')
  })

  it('exports useDeleteAdminLabel hook', () => {
    expect(adminLabels.useDeleteAdminLabel).toBeTypeOf('function')
  })

  it('exports useUpdateAdminLabelSortOrder hook', () => {
    expect(adminLabels.useUpdateAdminLabelSortOrder).toBeTypeOf('function')
  })
})
