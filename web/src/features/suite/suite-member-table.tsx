import { AlertTriangle, ArrowUpRight, CheckCircle2 } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { SkillSuite } from '@/api/types'
import { suiteBlockingReasonLabel } from '@/features/suite/suite-labels'
import { buttonVariants } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'

interface SuiteMemberTableProps {
  suite: SkillSuite
  returnTo: string
  management?: boolean
  onEdit?: () => void
}

export function SuiteMemberTable({ suite, returnTo, management = false, onEdit }: SuiteMemberTableProps) {
  const { t } = useTranslation()
  const availableCount = suite.members.filter(member => !member.blockingReason).length

  return (
    <section className="overflow-hidden rounded-lg border border-border bg-card">
      <div className="flex flex-col gap-2 border-b px-3 py-2.5 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-xs leading-5 text-muted-foreground">
          {t(management ? 'suite.management.membersSnapshotDescription' : 'suite.membersSnapshotDescription')}
        </p>
        {management && onEdit ? (
          <button type="button" className={cn(buttonVariants({ variant: 'soft', size: 'sm' }), 'h-6 px-2 text-[11px]')} onClick={onEdit}>
            {t('suite.management.editMembers')}
          </button>
        ) : null}
      </div>

      <div className="hidden grid-cols-[minmax(200px,1.25fr)_88px_minmax(160px,1fr)_100px_108px] gap-3 bg-secondary/25 px-3 py-1.5 text-[11px] text-muted-foreground md:grid">
        <span>{t('suite.management.member')}</span>
        <span>{t('suite.pinnedVersionColumn')}</span>
        <span>{t('suite.memberPurpose')}</span>
        <span>{t('suite.availability')}</span>
        <span className="text-right">{t('suite.memberAction')}</span>
      </div>

      <div className="divide-y divide-border/70">
        {suite.members.map((member) => {
          const coordinate = `@${member.namespace}/${member.slug}`
          const memberName = member.displayName || coordinate
          const navigable = Boolean(
            member.browsable && !member.blockingReason && member.skillId && member.skillVersionId,
          )
          const identity = (
            <div className="min-w-0">
              <div className="flex flex-wrap items-center gap-2">
                <span className={cn('truncate font-medium', navigable && 'text-blue-700 dark:text-blue-300')} title={memberName}>{memberName}</span>
                {member.entry ? (
                  <span className="rounded-md border border-blue-500/20 bg-blue-500/10 px-1.5 py-0.5 text-[11px] font-medium text-blue-700 dark:text-blue-300">
                    {t('suite.entrySkill')}
                  </span>
                ) : null}
              </div>
              <p className="mt-0.5 truncate font-mono text-xs text-muted-foreground" title={coordinate}>{coordinate}</p>
              {member.summary ? (
                <p className="mt-1 line-clamp-1 text-xs text-muted-foreground md:hidden">{member.summary}</p>
              ) : null}
            </div>
          )

          return (
            <div
              key={`${coordinate}@${member.version}`}
              className="grid gap-2 px-3 py-2 text-xs md:grid-cols-[minmax(200px,1.25fr)_88px_minmax(160px,1fr)_100px_108px] md:items-center md:gap-3"
            >
              {navigable ? (
                <Link
                  to="/space/$namespace/$slug"
                  params={{ namespace: member.namespace, slug: member.slug }}
                  search={{ returnTo, version: member.version }}
                  className="min-w-0 rounded-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
                >
                  {identity}
                </Link>
              ) : identity}
              <span className="font-mono text-xs">v{member.version}</span>
              <p className="line-clamp-2 text-xs leading-5 text-muted-foreground">
                {member.summary || t(member.entry ? 'suite.entryMemberPurpose' : 'suite.memberPurposeFallback')}
              </p>
              <span className={cn(
                'flex items-center gap-1.5 text-xs font-medium',
                member.blockingReason ? 'text-amber-700' : 'text-emerald-700',
              )}>
                {member.blockingReason
                  ? <AlertTriangle className="h-3.5 w-3.5" aria-hidden="true" />
                  : <CheckCircle2 className="h-3.5 w-3.5" aria-hidden="true" />}
                {member.blockingReason
                  ? suiteBlockingReasonLabel(t, member.blockingReason)
                  : t('suite.availableForSuite')}
              </span>
              <div className="text-left md:text-right">
                {navigable ? (
                  <Link
                    to="/space/$namespace/$slug"
                    params={{ namespace: member.namespace, slug: member.slug }}
                    search={{ returnTo, version: member.version }}
                    className="inline-flex items-center gap-1 text-xs font-medium text-blue-700 hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200"
                  >
                    {t('suite.viewPinnedVersion')}
                    <ArrowUpRight className="h-3.5 w-3.5" aria-hidden="true" />
                  </Link>
                ) : <span className="text-xs text-muted-foreground">{t('suite.memberUnavailable')}</span>}
              </div>
            </div>
          )
        })}
      </div>

      <div className="flex items-start gap-2 border-t bg-secondary/20 px-3 py-2 text-[11px] text-muted-foreground">
        <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-emerald-700" aria-hidden="true" />
        <p>
          <span className="font-medium text-foreground">
            {t('suite.availableMemberCount', { available: availableCount, total: suite.members.length })}
          </span>
          {' · '}{t(management ? 'suite.management.memberLifecycleHint' : 'suite.memberSnapshotHint')}
        </p>
      </div>
    </section>
  )
}
