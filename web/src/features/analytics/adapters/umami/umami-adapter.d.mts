export function sanitizeUmamiPayload(type: unknown, payload: unknown, website: unknown): { website: string; name: string; data: { event: string } } | null
export function createUmamiAdapter(options?: {
  window?: { umami?: unknown; skillhubUmamiBeforeSend?: unknown }
  document?: Document
  website?: unknown
  prefix?: string
  timeoutMs?: number
}): { send(intent?: { id?: string; event?: string; token?: string }): Promise<void>; stop(): void }
