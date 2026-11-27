import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowUpRight, GitBranch } from 'lucide-react'
import type { CrossSourceTwin } from './cross-source-twin'

/**
 * Non-blocking hint that the same slug exists in the other source. The two copies are independent
 * releases, so the reader is pointed at the other skill rather than a generated diff.
 */
export function CrossSourceTwinNotice({ twin }: { twin: CrossSourceTwin }) {
  const { t } = useTranslation()
  return (
    <div
      className="rounded-lg border border-border bg-secondary/30 px-4 py-3 text-sm"
      data-analytics-id="skill.crosssource.twin"
      role="note"
    >
      <p className="flex items-start gap-2 text-muted-foreground">
        <GitBranch className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
        <span>
          {t(twin.source === 'github' ? 'skillDetail.crossSourceTwinGithub' : 'skillDetail.crossSourceTwinPlatform', {
            namespace: twin.namespace,
          })}
        </span>
      </p>
      <Link
        className="mt-1 ml-6 inline-flex items-center gap-1 font-medium text-foreground underline underline-offset-4"
        data-analytics-id="skill.crosssource.twin.open"
        params={{ namespace: twin.namespace, slug: twin.slug }}
        to="/space/$namespace/$slug"
      >
        {t('skillDetail.crossSourceTwinOpen')}
        <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
      </Link>
    </div>
  )
}
