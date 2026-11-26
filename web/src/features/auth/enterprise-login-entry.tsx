import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Search } from 'lucide-react'
import { authApi, ApiError } from '@/api/client'
import type { EnterpriseLoginDiscovery } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { withBasePath } from '@/shared/lib/base-path'

interface EnterpriseLoginEntryProps {
  returnTo?: string
}

/** Organization-first OIDC entry. It only reveals public connection labels and opaque actions. */
export function EnterpriseLoginEntry({ returnTo }: EnterpriseLoginEntryProps) {
  const { t } = useTranslation()
  const [identifier, setIdentifier] = useState('')
  const [result, setResult] = useState<EnterpriseLoginDiscovery | null>(null)
  const [error, setError] = useState(false)
  const [loading, setLoading] = useState(false)

  async function discover(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const value = identifier.trim()
    if (!value) return
    setLoading(true)
    setError(false)
    try {
      setResult(await authApi.discoverEnterprise(value, returnTo))
    } catch (cause) {
      setResult(null)
      setError(cause instanceof ApiError || cause instanceof Error)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="space-y-4">
      <div className="space-y-1">
        <h2 className="text-base font-semibold text-foreground">{t('login.enterpriseTitle')}</h2>
        <p className="text-sm text-muted-foreground">{t('login.enterpriseHint')}</p>
      </div>
      <form className="space-y-3" onSubmit={discover}>
        <label className="space-y-2 text-sm font-medium" htmlFor="enterprise-identifier">
          <span>{t('login.enterpriseIdentifier')}</span>
          <Input
            id="enterprise-identifier"
            value={identifier}
            onChange={(event) => setIdentifier(event.target.value)}
            placeholder={t('login.enterpriseIdentifierPlaceholder')}
            autoComplete="organization"
          />
        </label>
        <Button className="h-11 w-full xl:h-10" disabled={loading || !identifier.trim()} type="submit">
          <Search aria-hidden="true" className="h-4 w-4" />
          {loading ? t('login.enterpriseSearching') : t('login.enterpriseFind')}
        </Button>
      </form>

      {error ? <p role="alert" className="text-sm text-red-600">{t('login.enterpriseDiscoveryUnavailable')}</p> : null}
      {result && result.organizations.length === 0 ? (
        <p role="status" className="rounded-lg border border-border bg-muted/40 px-3 py-2 text-sm text-muted-foreground">
          {t('login.noEnterpriseMethods')}
        </p>
      ) : null}
      {result?.organizations.map((organization) => (
        <section key={organization.slug} className="space-y-2 rounded-xl border border-border bg-muted/20 p-3" aria-label={organization.displayName}>
          <div>
            <p className="text-sm font-medium text-foreground">{organization.displayName}</p>
            <p className="text-xs text-muted-foreground">{organization.slug}</p>
          </div>
          <div className="grid gap-2">
            {organization.loginOptions.map((option) => (
              <Button
                key={option.actionUrl}
                type="button"
                variant="outline"
                className="w-full justify-between"
                onClick={() => { window.location.href = withBasePath(option.actionUrl) }}
              >
                {option.displayName}
                <span aria-hidden="true">→</span>
              </Button>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
