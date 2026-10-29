import { adapters } from './registry'
import type { AnalyticsConfig, Diagnostic } from './types'

function object(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
    && [Object.prototype, null].includes(Object.getPrototypeOf(value))
}
export function parseAnalyticsConfig(raw: unknown): { config: AnalyticsConfig | null; diagnostics: Diagnostic[] } {
  const diagnostics: Diagnostic[] = []
  const fail = (field: string, reason: string) => { diagnostics.push({ field, reason }) }
  if (raw === undefined || raw === '') return { config: null, diagnostics }
  try {
    const value: unknown = typeof raw === 'string' ? JSON.parse(raw) : raw
    if (!object(value)) { fail('config', 'expected object'); return { config: null, diagnostics } }
    const { enabled = false, consent = 'denied', adapter, adapterConfig, rules } = value
    if (typeof enabled !== 'boolean') fail('enabled', 'expected boolean')
    if (consent !== 'denied' && consent !== 'granted') fail('consent', 'expected denied or granted')
    if (Object.keys(value).some(key => !['enabled', 'adapter', 'consent', 'rules', 'adapterConfig'].includes(key))) fail('config', 'unknown field')
    if (adapter !== undefined && (typeof adapter !== 'string' || !Object.prototype.hasOwnProperty.call(adapters, adapter))) fail('adapter', 'not registered')
    if (adapterConfig !== undefined && !object(adapterConfig)) fail('adapterConfig', 'expected object')
    if (enabled === true) {
      if (typeof adapter !== 'string' || !Object.prototype.hasOwnProperty.call(adapters, adapter)) fail('adapter', 'required registered adapter')
      else if (!object(adapterConfig) || !adapters[adapter].validate(adapterConfig)) fail('adapterConfig', 'invalid public settings')
    }
    return { config: diagnostics.length ? null : { enabled, consent, adapter, adapterConfig, ...(Object.prototype.hasOwnProperty.call(value, 'rules') ? { rules } : {}) } as AnalyticsConfig, diagnostics }
  } catch { fail('config', 'invalid JSON or unreadable object'); return { config: null, diagnostics } }
}
export function reportDiagnostics(diagnostics: Diagnostic[]) {
  for (const { field, reason } of diagnostics) console.error(`[analytics] ${field}: ${reason}`)
}
