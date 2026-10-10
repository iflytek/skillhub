export function resolveReviewActionErrorDescription(error: unknown): string | undefined {
  if (!(error instanceof Error)) {
    return undefined
  }

  const message = error.message.trim()
  return message || undefined
}
