import { afterEach, describe, expect, it, vi } from 'vitest'

afterEach(() => {
  vi.unstubAllGlobals()
})

// The use-skill-reports module exports thin useQuery / useMutation wrappers
// (useSkillReports, useSubmitSkillReport, useResolveSkillReport, useDismissSkillReport).
// These are wrappers around reportApi methods with standard cache invalidation.
//
// The tests below verify exports and the underlying reportApi surface.

describe('use-skill-reports exports', () => {
  it('exports useSkillReports', async () => {
    const mod = await import('./use-skill-reports')
    expect(mod.useSkillReports).toBeDefined()
    expect(typeof mod.useSkillReports).toBe('function')
  })

  it('exports useSubmitSkillReport', async () => {
    const mod = await import('./use-skill-reports')
    expect(mod.useSubmitSkillReport).toBeDefined()
    expect(typeof mod.useSubmitSkillReport).toBe('function')
  })

  it('exports useResolveSkillReport', async () => {
    const mod = await import('./use-skill-reports')
    expect(mod.useResolveSkillReport).toBeDefined()
    expect(typeof mod.useResolveSkillReport).toBe('function')
  })

  it('exports useDismissSkillReport', async () => {
    const mod = await import('./use-skill-reports')
    expect(mod.useDismissSkillReport).toBeDefined()
    expect(typeof mod.useDismissSkillReport).toBe('function')
  })
})

describe('reportApi methods', () => {
  it('reportApi exports listSkillReports', async () => {
    const { reportApi } = await import('@/api/client')
    expect(reportApi.listSkillReports).toBeDefined()
    expect(typeof reportApi.listSkillReports).toBe('function')
  })

  it('reportApi exports submitSkillReport', async () => {
    const { reportApi } = await import('@/api/client')
    expect(reportApi.submitSkillReport).toBeDefined()
    expect(typeof reportApi.submitSkillReport).toBe('function')
  })

  it('reportApi exports resolveSkillReport', async () => {
    const { reportApi } = await import('@/api/client')
    expect(reportApi.resolveSkillReport).toBeDefined()
    expect(typeof reportApi.resolveSkillReport).toBe('function')
  })

  it('reportApi exports dismissSkillReport', async () => {
    const { reportApi } = await import('@/api/client')
    expect(reportApi.dismissSkillReport).toBeDefined()
    expect(typeof reportApi.dismissSkillReport).toBe('function')
  })
})
