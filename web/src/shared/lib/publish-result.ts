import type { PublishResult, PublishResultItem } from '@/api/types'

export function isPublishResultItem(value: PublishResult): value is PublishResultItem {
  return typeof value.skillId === 'number'
}

export function getPublishResultItems(result: PublishResult): PublishResultItem[] {
  if (Array.isArray(result.results) && result.results.length > 0) {
    return result.results
  }

  return [result]
}

export function formatPublishResultLabel(result: PublishResultItem): string {
  return `${result.namespace}/${result.slug}@${result.version}`
}
