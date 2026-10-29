// Public settings only. Never accept a caller-supplied script URL or proxy prefix.
export function validateUmamiConfig(config: Record<string, unknown>): boolean {
  return typeof config.websiteId === 'string'
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(config.websiteId)
    && Object.keys(config).every(key => key === 'websiteId')
}
