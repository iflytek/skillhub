export type PresentationSource = 'package' | 'custom'
export function presentationPayload(shortSource: PresentationSource, overviewSource: PresentationSource, short: string, overview: string) {
  return { shortDescription: shortSource === 'custom' ? short : undefined, overview: overviewSource === 'custom' ? overview : undefined, resetPresentation: shortSource === 'package' && overviewSource === 'package' }
}
export function publishedOverrides(snapshot?: string): { short?: string; overview?: string } {
  try {
    const value: unknown = JSON.parse(snapshot || '{}')
    if (!value || typeof value !== 'object') return {}
    const data = value as Record<string, unknown>
    return { short: typeof data.shortDescriptionOverride === 'string' ? data.shortDescriptionOverride : undefined, overview: typeof data.overviewOverride === 'string' ? data.overviewOverride : undefined }
  } catch { return {} }
}
