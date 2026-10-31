import { validateUmamiConfig } from './adapters/umami/config'
import type { AdapterFactory } from './types'

// Static allowlist: metadata validation is tiny; adapter code is a lazy chunk.
export const adapters: Readonly<Record<string, { validate(config: Record<string, unknown>): boolean; create: AdapterFactory }>> = Object.freeze({
  umami: {
    validate: validateUmamiConfig,
    create: async (config, prefix, signal) => {
      const { createUmamiAdapter } = await import('./adapters/umami/umami-adapter.mjs')
      if (signal?.aborted) return { send: async () => {}, stop() {} }
      return createUmamiAdapter({ website: config.websiteId, prefix })
    },
  },
})
