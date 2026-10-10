import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import { Link } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import type { SkillSuite, SkillSuiteMember, SkillSuiteVersion } from '@/api/types'
import { suiteStatusLabel } from '@/features/suite/suite-labels'
import { useSuiteDetail } from '@/shared/hooks/use-suite-queries'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { cn } from '@/shared/lib/utils'
import { Button } from '@/shared/ui/button'

interface SuiteVersionLedgerProps {
  namespace: string
  slug: string
  versions: SkillSuiteVersion[]
  currentSuite: SkillSuite
  returnTo: string
  management?: boolean
  onSelectVersion: (version: string) => void
  onEditVersion?: (suite: SkillSuite) => void
  onSubmitVersion?: (suite: SkillSuite) => void
}

type MemberChange = {
  member: SkillSuiteMember
  kind: 'ADDED' | 'UPDATED' | 'UNCHANGED' | 'REMOVED'
  previousVersion?: string
}

function memberKey(member: SkillSuiteMember) {
  return `${member.namespace}/${member.slug}`
}

function versionStatusTone(status: string) {
  switch (status) {
    case 'PUBLISHED':
      return 'border-emerald-500/20 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300'
    case 'YANKED':
    case 'PENDING_REVIEW':
    case 'SCANNING':
      return 'border-amber-500/20 bg-amber-500/10 text-amber-700 dark:text-amber-300'
    case 'REJECTED':
      return 'border-red-500/20 bg-red-500/10 text-red-700 dark:text-red-300'
    default:
      return 'border-blue-500/20 bg-blue-500/10 text-blue-700 dark:text-blue-300'
  }
}

function compareMembers(current: SkillSuiteMember[], previous: SkillSuiteMember[]): MemberChange[] {
  const previousByKey = new Map(previous.map(member => [memberKey(member), member]))
  const currentKeys = new Set(current.map(memberKey))
  const changes: MemberChange[] = current.map((member) => {
    const prior = previousByKey.get(memberKey(member))
    if (!prior) return { member, kind: 'ADDED' as const }
    return prior.version === member.version
      ? { member, kind: 'UNCHANGED' as const }
      : { member, kind: 'UPDATED' as const, previousVersion: prior.version }
  })
  for (const member of previous) {
    if (!currentKeys.has(memberKey(member))) changes.push({ member, kind: 'REMOVED' })
  }
  return changes
}

export function SuiteVersionLedger({
  namespace,
  slug,
  versions,
  currentSuite,
  returnTo,
  management = false,
  onSelectVersion,
  onEditVersion,
  onSubmitVersion,
}: SuiteVersionLedgerProps) {
  const { t, i18n } = useTranslation()
  const [expandedVersion, setExpandedVersion] = useState(currentSuite.version)

  return (
    <section className="overflow-hidden rounded-lg border border-border bg-card">
      <div className="hidden grid-cols-[84px_104px_minmax(112px,1fr)_136px_88px] gap-2.5 bg-secondary/25 px-3 py-1.5 text-[11px] text-muted-foreground md:grid">
        <span>{t('suite.version')}</span>
        <span>{t('suite.lifecycleStatus')}</span>
        <span>{t('suite.createdBy')}</span>
        <span>{t('suite.updatedAt')}</span>
        <span className="text-right">{t('suite.memberAction')}</span>
      </div>
      <div className="divide-y divide-border/70">
        {versions.map((version, index) => {
          const expanded = expandedVersion === version.version
          const previousVersion = versions[index + 1]?.version
          return (
            <div key={version.id}>
              <div className="grid gap-1.5 px-3 py-2 text-xs md:grid-cols-[84px_104px_minmax(112px,1fr)_136px_88px] md:items-center md:gap-2.5">
                <button
                  type="button"
                  className="flex items-center gap-1.5 text-left font-mono font-semibold focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70"
                  aria-expanded={expanded}
                  onClick={() => setExpandedVersion(expanded ? '' : version.version)}
                >
                  {expanded
                    ? <ChevronDown className="h-3.5 w-3.5" aria-hidden="true" />
                    : <ChevronRight className="h-3.5 w-3.5" aria-hidden="true" />}
                  v{version.version}
                </button>
                <span className={cn(
                  'w-fit rounded-md border px-1.5 py-0.5 text-[11px] font-medium',
                  versionStatusTone(version.status),
                )}>
                  {suiteStatusLabel(t, version.status)}
                </span>
                <span className="truncate text-[11px]" title={version.createdByName || version.createdBy}>
                  {version.createdByName || version.createdBy}
                </span>
                <span className="text-[11px] text-muted-foreground">
                  {formatLocalDateTime(version.publishedAt || version.createdAt, i18n.language)}
                </span>
                <Button
                  type="button"
                  size="sm"
                  variant="soft"
                  className="h-6 w-fit justify-center rounded-md px-2 text-[11px] md:w-full"
                  onClick={() => onSelectVersion(version.version)}
                >
                  {t('suite.viewVersion')}
                </Button>
              </div>
              {expanded ? (
                <ExpandedVersion
                  namespace={namespace}
                  slug={slug}
                  version={version}
                  previousVersion={previousVersion}
                  currentSuite={currentSuite}
                  returnTo={returnTo}
                  management={management}
                  onEditVersion={onEditVersion}
                  onSubmitVersion={onSubmitVersion}
                />
              ) : null}
            </div>
          )
        })}
      </div>
    </section>
  )
}

function ExpandedVersion({
  namespace,
  slug,
  version,
  previousVersion,
  currentSuite,
  returnTo,
  management,
  onEditVersion,
  onSubmitVersion,
}: {
  namespace: string
  slug: string
  version: SkillSuiteVersion
  previousVersion?: string
  currentSuite: SkillSuite
  returnTo: string
  management: boolean
  onEditVersion?: (suite: SkillSuite) => void
  onSubmitVersion?: (suite: SkillSuite) => void
}) {
  const { t } = useTranslation()
  const isCurrent = currentSuite.version === version.version
  const { data: loadedSuite, isLoading } = useSuiteDetail(namespace, slug, version.version, !isCurrent)
  const detail = isCurrent ? currentSuite : loadedSuite
  const { data: previous } = useSuiteDetail(namespace, slug, previousVersion, Boolean(previousVersion && detail))
  const changes = detail ? compareMembers(detail.members, previous?.members ?? []) : []

  return (
    <div className="border-t border-blue-500/10 bg-blue-500/[0.025] px-3 py-3">
      {isLoading || !detail ? (
        <div className="h-20 animate-shimmer rounded-md" aria-label={t('suite.loadingVersionDetails')} />
      ) : (
        <div className="grid min-w-0 gap-3 md:grid-cols-[minmax(180px,0.7fr)_minmax(360px,1.3fr)]">
          <section className="min-w-0">
            <h3 className="text-xs font-semibold">{t('suite.changelog')}</h3>
            <p className="mt-2 whitespace-pre-wrap break-words text-xs leading-5 text-muted-foreground">
              {version.changelog || t('suite.noChangelog')}
            </p>
            {management ? (
              <div className="mt-3 flex flex-wrap gap-1.5">
                {detail.allowedActions.includes('EDIT') && onEditVersion ? (
                  <Button size="sm" variant="soft" className="h-6 px-2 text-[11px]" onClick={() => onEditVersion(detail)}>
                    {t('suite.editDraft')}
                  </Button>
                ) : null}
                {(detail.allowedActions.includes('SUBMIT') || detail.allowedActions.includes('PUBLISH_PRIVATE'))
                  && onSubmitVersion ? (
                    <Button size="sm" className="h-6 px-2 text-[11px]" onClick={() => onSubmitVersion(detail)}>
                      {detail.visibility === 'PRIVATE' ? t('suite.publishDirectly') : t('suite.submitReview')}
                    </Button>
                ) : null}
              </div>
            ) : null}
          </section>
          <section className="min-w-0">
            <h3 className="text-xs font-semibold">
              {t('suite.memberInformation', { count: detail.members.length })}
            </h3>
            <div className="mt-2 overflow-hidden rounded-md border border-border/70 bg-background">
              <div className="hidden grid-cols-[minmax(180px,1fr)_80px_80px_96px] gap-2.5 bg-secondary/30 px-2.5 py-1.5 text-[11px] text-muted-foreground sm:grid">
                <span>{t('suite.management.member')}</span>
                <span>{t('suite.pinnedVersionColumn')}</span>
                <span>{t('suite.management.role')}</span>
                <span>{t('suite.memberChange')}</span>
              </div>
              <div className="divide-y divide-border/60">
                {changes.map(({ member, kind, previousVersion: prior }) => {
                  const navigable = Boolean(member.browsable && !member.blockingReason && member.skillId && member.skillVersionId)
                  return (
                    <div key={`${memberKey(member)}-${kind}`} className="grid gap-1.5 px-2.5 py-1.5 text-[11px] sm:grid-cols-[minmax(180px,1fr)_80px_80px_96px] sm:items-center sm:gap-2.5">
                      {navigable ? (
                        <Link
                          to="/space/$namespace/$slug"
                          params={{ namespace: member.namespace, slug: member.slug }}
                          search={{ returnTo, version: member.version }}
                          className="truncate font-mono text-blue-700 hover:text-blue-800 hover:underline dark:text-blue-300 dark:hover:text-blue-200"
                        >
                          @{member.namespace}/{member.slug}
                        </Link>
                      ) : <span className="truncate font-mono">@{member.namespace}/{member.slug}</span>}
                      <span className="font-mono">v{member.version}</span>
                      <span>{member.entry ? t('suite.entrySkill') : t('suite.management.memberRole')}</span>
                      <span className={cn(
                        kind === 'ADDED' && 'text-emerald-700 dark:text-emerald-300',
                        kind === 'UPDATED' && 'text-blue-700 dark:text-blue-300',
                        kind === 'REMOVED' && 'text-red-700 dark:text-red-300',
                      )}>
                        {t(`suite.memberChangeKinds.${kind}`, { version: prior })}
                      </span>
                    </div>
                  )
                })}
              </div>
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
