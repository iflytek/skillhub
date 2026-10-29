import type { SkillhubIntent } from './skillhub-intents.mjs'

export interface AnalyticsConfig {
  enabled: boolean
  adapter?: string
  consent: 'denied' | 'granted'
  rules?: unknown
  adapterConfig?: Record<string, unknown>
}
export interface AnalyticsAdapter {
  send(intent: SkillhubIntent): Promise<void>
  stop(): void
}
export type AdapterFactory = (config: Record<string, unknown>, prefix: string, signal?: AbortSignal) => Promise<AnalyticsAdapter>
export interface AnalyticsAPI { start(): Promise<void>; stop(): void }
export interface Diagnostic { field: string; reason: string }
