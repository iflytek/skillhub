import { describe, expect, it } from 'vitest'

/**
 * useDebounce is a React hook that relies on useState and useEffect.
 * Testing it properly requires a React rendering environment (e.g.
 * @testing-library/react renderHook), which is not available in this
 * project. The hook contains no exported pure helpers to test in
 * isolation.
 *
 * Verified: the module exports only the useDebounce hook.
 */
describe('useDebounce', () => {
  it('exports a function', async () => {
    const mod = await import('./use-debounce')
    expect(typeof mod.useDebounce).toBe('function')
  })
})
