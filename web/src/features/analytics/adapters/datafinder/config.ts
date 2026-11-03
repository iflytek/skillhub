import type { Diagnostic } from '../../types'

// Public browser settings only. Server ingestion credentials are not supported.
export function datafinderConfigDiagnostics(config: Record<string, unknown>): Diagnostic[] {
  if (Object.keys(config).some(key => !['appId', 'product'].includes(key))) return [{ field: 'adapterConfig', reason: 'unknown field' }]
  if (typeof config.appId !== 'number' || !Number.isInteger(config.appId) || config.appId < 1 || config.appId > 2147483647) {
    return [{ field: 'adapterConfig.appId', reason: 'expected positive 32-bit integer' }]
  }
  if (Object.prototype.hasOwnProperty.call(config, 'product') && (typeof config.product !== 'string' || !/^[a-z][a-z0-9_]{0,31}$/.test(config.product))) {
    return [{ field: 'adapterConfig.product', reason: 'expected lowercase product name' }]
  }
  return []
}
export function validateDatafinderConfig(config: Record<string, unknown>): boolean {
  return datafinderConfigDiagnostics(config).length === 0
}
