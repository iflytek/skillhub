import type { AuthMethod } from '@/api/types'

export interface LocalAuthAvailability {
  login: boolean
  registration: boolean
}

/**
 * Derives whether the local password form and self-registration should be offered.
 *
 * Both stay available until the backend catalog has loaded, so a slow or failed catalog request
 * never hides the only way into a default deployment. The server rejects disabled flows anyway.
 */
export function getLocalAuthAvailability(methods: AuthMethod[] | undefined, catalogFailed = false): LocalAuthAvailability {
  if (!methods || catalogFailed) {
    return { login: true, registration: true }
  }
  return {
    login: methods.some((method) => method.methodType === 'PASSWORD'),
    registration: methods.some((method) => method.methodType === 'PASSWORD_REGISTRATION'),
  }
}
