import { describe, expect, it, vi } from 'vitest'

// The i18n config module performs side-effect-only initialization.
// We mock i18next to verify that init is called with expected config.

const initMock = vi.fn().mockReturnThis()
const useMock = vi.fn().mockReturnThis()

vi.mock('i18next', () => ({
  default: {
    use: useMock,
    init: initMock,
  },
}))

vi.mock('react-i18next', () => ({
  initReactI18next: { type: '3rdParty', init: vi.fn() },
}))

vi.mock('i18next-browser-languagedetector', () => ({
  default: class MockDetector {},
}))

vi.mock('./locales/en.json', () => ({
  default: { greeting: 'Hello' },
}))

vi.mock('./locales/zh.json', () => ({
  default: { greeting: '你好' },
}))

// Import triggers the side-effect initialization
await import('./config')

describe('i18n config', () => {
  it('chains the language detector and react-i18next plugins', () => {
    expect(useMock).toHaveBeenCalledTimes(2)
  })

  it('calls init with the english fallback language', () => {
    expect(initMock).toHaveBeenCalledTimes(1)
    const initOptions = initMock.mock.calls[0][0]
    expect(initOptions.fallbackLng).toBe('en')
  })

  it('disables HTML escaping for React interpolation', () => {
    const initOptions = initMock.mock.calls[0][0]
    expect(initOptions.interpolation.escapeValue).toBe(false)
  })

  it('configures localStorage-first detection order', () => {
    const initOptions = initMock.mock.calls[0][0]
    expect(initOptions.detection.order).toEqual(['localStorage', 'navigator'])
    expect(initOptions.detection.caches).toEqual(['localStorage'])
  })

  it('registers both english and chinese resource bundles', () => {
    const initOptions = initMock.mock.calls[0][0]
    expect(initOptions.resources).toHaveProperty('en')
    expect(initOptions.resources).toHaveProperty('zh')
    expect(initOptions.resources.en).toHaveProperty('translation')
    expect(initOptions.resources.zh).toHaveProperty('translation')
  })
})
