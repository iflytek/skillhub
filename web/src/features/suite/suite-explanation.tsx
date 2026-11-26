import { useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'
import type { components } from '@/api/generated/schema'
import type { SkillSuite } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { Button } from '@/shared/ui/button'

export function SuiteExplanation({ suite }: { suite: SkillSuite }) {
  const { user } = useAuth()
  const { i18n } = useTranslation()
  if (!user) return null
  return <ExplanationPanel key={JSON.stringify([suite.versionId, user.userId, i18n.language])} suite={suite} />
}

function ExplanationPanel({ suite }: { suite: SkillSuite }) {
  const { t, i18n } = useTranslation()
  const query = useMutation({
    mutationFn: () => fetchJson<components['schemas']['Explanation']>(
      `${WEB_API_PREFIX}/suites/${encodeURIComponent(suite.namespace)}/${encodeURIComponent(suite.slug)}/explanation?version=${encodeURIComponent(suite.version)}`,
      { method: 'POST', headers: getCsrfHeaders({ 'Accept-Language': i18n.language }), timeoutMs: 45_000 },
    ),
    retry: false,
    meta: { skipGlobalErrorHandler: true },
  })
  return (
    <section className="mb-4">
      <Button size="sm" variant="outline" data-analytics-id="suite.explanation.request"
        disabled={query.isPending} onClick={() => query.mutate()}>
        {t(query.isPending ? 'suite.explanationLoading' : 'suite.explainWithAi')}
      </Button>
      <div aria-live="polite">
        {query.isError && <p className="mt-2 text-sm text-destructive">{t('suite.explanationFailed')}</p>}
        {query.data?.statements?.map((statement, index) => (
          <p key={index} className="mt-2 text-sm">
            {statement.text}{' '}
            {statement.sources?.map(source => {
              if (!source.namespace || !source.slug || !source.version) return null
              const params = { namespace: source.namespace, slug: source.slug }
              return <Link key={JSON.stringify(params)} className="mr-2 underline"
                to={source.suite ? '/suite/$namespace/$slug' : '/space/$namespace/$slug'}
                params={params} search={{ version: source.version }}>
                @{source.namespace}/{source.slug}@{source.version}{source.suite ? '' : ' · SKILL.md'}
              </Link>
            })}
          </p>
        ))}
      </div>
    </section>
  )
}
