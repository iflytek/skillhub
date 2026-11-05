import { useEffect } from 'react'
import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'

/** Compatibility entry: a namespace selects the shared queue's scope, not another workbench. */
export function NamespaceReviewsPage() {
  const { slug } = useParams({ from: '/dashboard/namespaces/$slug/reviews' })
  const search = useSearch({ from: '/dashboard/namespaces/$slug/reviews' })
  const navigate = useNavigate()
  const { t } = useTranslation()
  useEffect(() => {
    void navigate({ to: '/dashboard/reviews', search: { namespace: slug, originType: search.queue === 'sync' ? 'GITHUB_SYNC' : undefined }, replace: true })
  }, [navigate, slug, search.queue])
  return <p className="py-6 text-sm text-muted-foreground" role="status">{t('reviewCenter.openingScope')}</p>
}
