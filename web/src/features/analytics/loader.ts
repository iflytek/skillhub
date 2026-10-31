import { parseAnalyticsConfig, reportDiagnostics } from './config'
import type { AnalyticsAPI } from './types'
import type { createAnalyticsController } from './controller'

type Module = { createAnalyticsController: typeof createAnalyticsController }
export function installAnalyticsLoader(read: () => unknown, load: () => Promise<Module> = () => import('./controller')): AnalyticsAPI {
  let generation = 0
  let controller: AnalyticsAPI | undefined
  let pending: Promise<void> | undefined
  function stop() {
    generation++
    pending = undefined
    controller?.stop()
    controller = undefined
  }
  function start(explicitConsent = true): Promise<void> {
    if (pending) return pending
    let raw: unknown
    try { raw = read() } catch {
      console.error('[analytics] config: unreadable configuration')
      return Promise.resolve()
    }
    const { config, diagnostics } = parseAnalyticsConfig(raw)
    reportDiagnostics(diagnostics)
    if (!config?.enabled || (!explicitConsent && config.consent !== 'granted')) return Promise.resolve()
    const own = ++generation
    pending = (async () => {
      try {
        const module = await load()
        if (own !== generation) return
        controller = module.createAnalyticsController({ ...config, consent: 'granted' })
        await controller.start()
      } catch { console.error('[analytics] module: initialization failed') }
    })()
    return pending
  }
  void start(false)
  return Object.freeze({ start: () => start(true), stop })
}

// React render schedules a commit. Observe the first committed DOM, not just the
// resolution of import(main); never make mounting wait for analytics or its SDK.
export function afterAppMount(root: Element, run: () => void) {
  const schedule = () => { window.setTimeout(run, 0) }
  if (root.hasChildNodes()) { schedule(); return }
  const observer = new MutationObserver(() => {
    if (!root.hasChildNodes()) return
    observer.disconnect()
    schedule()
  })
  observer.observe(root, { childList: true })
}
