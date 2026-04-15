import { ApiError } from '@/shared/lib/api-error'

export type CollectionFormField = 'title' | 'description' | 'visibility' | 'slug'

/**
 * Server validation returns a single message (first field error). Map it to an inline field when possible.
 */
export function mapCollectionMutationError(
  error: unknown,
): { fieldErrors: Partial<Record<CollectionFormField, string>>; formError?: string } {
  if (!(error instanceof ApiError)) {
    return { fieldErrors: {}, formError: String(error) }
  }

  const text = (error.serverMessage ?? error.message ?? '').trim()
  if (!text) {
    return { fieldErrors: {}, formError: error.message }
  }

  const field = inferCollectionField(text)
  if (field) {
    return { fieldErrors: { [field]: text } }
  }

  return { fieldErrors: {}, formError: text }
}

function inferCollectionField(message: string): CollectionFormField | null {
  const lower = message.toLowerCase()
  if (lower.includes('visibility') || message.includes('可见性')) {
    return 'visibility'
  }
  if (lower.includes('slug') || message.includes('短链') || message.includes('标识')) {
    return 'slug'
  }
  if (lower.includes('description') || message.includes('描述')) {
    return 'description'
  }
  if (lower.includes('title') || message.includes('标题')) {
    return 'title'
  }
  return null
}
