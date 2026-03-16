export const MAX_ERROR_MESSAGE_LENGTH = 180

export function truncateErrorMessage(message?: string): string | undefined {
  if (!message) {
    return undefined
  }

  const normalizedMessage = message.trim()
  if (!normalizedMessage) {
    return undefined
  }

  if (normalizedMessage.length <= MAX_ERROR_MESSAGE_LENGTH) {
    return normalizedMessage
  }

  return `${normalizedMessage.slice(0, MAX_ERROR_MESSAGE_LENGTH)}...`
}
