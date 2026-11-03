import { validateUmamiConfig } from './adapters/umami/config'
import { validateDatafinderConfig, datafinderConfigDiagnostics } from './adapters/datafinder/config'
import type { AdapterFactory, Diagnostic } from './types'

// Static allowlist: metadata validation is tiny; adapter code is a lazy chunk.
export const adapters: Readonly<Record<string, { validate(config: Record<string, unknown>): boolean; diagnostics?(config: Record<string, unknown>): Diagnostic[]; create: AdapterFactory }>> = Object.freeze({
  datafinder: {
    validate: validateDatafinderConfig,
    diagnostics: datafinderConfigDiagnostics,
    create: async (config, prefix, signal, context) => {
      const { createDatafinderAdapter } = await import('./adapters/datafinder/adapter')
      if (signal?.aborted) return { send: async () => {}, stop() {} }
      return createDatafinderAdapter(config, prefix, context)
    },
  },
  umami: {
    validate: validateUmamiConfig,
    create: async (config, prefix, signal) => {
      const { createUmamiAdapter } = await import('./adapters/umami/umami-adapter.mjs')
      if (signal?.aborted) return { send: async () => {}, stop() {} }
      return createUmamiAdapter({ website: config.websiteId, prefix })
    },
  },
})
