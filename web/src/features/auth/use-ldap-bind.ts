import { useMutation } from '@tanstack/react-query'
import { ldapApi } from '@/api/client'
import type { LdapBindRequest } from '@/api/types'

/**
 * Binds the LDAP identity verified by the given directory credentials to the currently
 * authenticated account.
 */
export function useLdapBind() {
  return useMutation({
    mutationFn: (request: LdapBindRequest) => ldapApi.bindIdentity(request),
  })
}
