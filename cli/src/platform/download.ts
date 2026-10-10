import { EXIT } from '../shared/constants'
import { CliError } from '../shared/errors'

export const MAX_PACKAGE_BYTES = 100 * 1024 * 1024

export async function readBoundedResponseBody(response: Response, maxBytes = MAX_PACKAGE_BYTES): Promise<ArrayBuffer> {
  const contentLength = response.headers.get('content-length')
  if (contentLength !== null) {
    const declaredLength = Number(contentLength)
    if (Number.isFinite(declaredLength) && declaredLength > maxBytes) {
      throw new CliError('download exceeds maximum package size', EXIT.network, {
        contentLength: declaredLength,
        maxBytes
      })
    }
  }

  if (!response.body) {
    const buffer = await response.arrayBuffer()
    if (buffer.byteLength > maxBytes) {
      throw new CliError('download exceeds maximum package size', EXIT.network, {
        receivedBytes: buffer.byteLength,
        maxBytes
      })
    }
    return buffer
  }

  const reader = response.body.getReader()
  const chunks: Uint8Array[] = []
  let receivedBytes = 0

  for (;;) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    receivedBytes += value.byteLength
    if (receivedBytes > maxBytes) {
      await reader.cancel()
      throw new CliError('download exceeds maximum package size', EXIT.network, {
        receivedBytes,
        maxBytes
      })
    }
    chunks.push(value)
  }

  const result = new Uint8Array(receivedBytes)
  let offset = 0
  for (const chunk of chunks) {
    result.set(chunk, offset)
    offset += chunk.byteLength
  }
  return result.buffer
}
