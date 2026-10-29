import { DEFAULT_RULES, observeSkillhubIntents, resolveRules } from './skillhub-intents.mjs'
import { adapters } from './registry'
import type { AdapterFactory, AnalyticsAPI, AnalyticsConfig } from './types'

export function createAnalyticsController(config: AnalyticsConfig, options: { root?: Document | Element; factory?: AdapterFactory; prefix?: string } = {}): AnalyticsAPI {
  let generation = 0
  let pending: Promise<void> | undefined
  let dispose: (() => void) | undefined
  let cancellation: AbortController | undefined
  const root = options.root ?? document
  function stop() {
    generation++
    cancellation?.abort()
    pending = undefined
    dispose?.()
    dispose = undefined
  }
  function start(): Promise<void> {
    if (pending) return pending
    if (!config.enabled || config.consent !== 'granted') return Promise.resolve()
    const own = ++generation
    cancellation = new AbortController()
    const signal = cancellation.signal
    const { active, skipped } = resolveRules(config.rules === undefined ? DEFAULT_RULES : config.rules)
    if (skipped.length) console.error('[analytics] rules: unsupported entries skipped')
    const factory = options.factory ?? adapters[config.adapter!]?.create
    pending = (async () => {
      try {
        const adapter = await factory(config.adapterConfig ?? {}, options.prefix ?? `${import.meta.env.BASE_URL}_skillhub-analytics`, signal)
        if (own !== generation) { adapter.stop(); return }
        const stopObserver = observeSkillhubIntents({ root, rules: active, onIntent: intent => adapter.send(intent) })
        dispose = () => { stopObserver(); try { adapter.stop() } catch { /* isolated */ } }
      } catch { console.error('[analytics] adapter: initialization failed') }
    })()
    return pending
  }
  return { start, stop }
}
