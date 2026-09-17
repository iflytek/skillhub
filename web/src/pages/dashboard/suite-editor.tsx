import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowDown, ArrowUp, CircleDot, Plus, Search, Trash2, X } from 'lucide-react'
import type { SkillSuiteDraftInput, SkillSuiteMemberCandidate, SkillSuiteMemberInput } from '@/api/types'
import {
  useCreateSuite,
  useCreateSuiteVersion,
  useSuiteDetail,
  useSuiteMemberCandidates,
  useUpdateSuiteDraft,
} from '@/shared/hooks/use-suite-queries'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { SuiteWorkspaceHeader } from '@/features/suite/suite-workspace-header'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { toast } from '@/shared/lib/toast'
import {
  hasStoredSuiteBundleOperation,
  SuiteBundleImport,
} from '@/features/suite/suite-bundle-import'
import { SuiteLabelPanel } from '@/features/skill/skill-label-panel'
import { useSuiteLabels } from '@/shared/hooks/use-label-queries'
import { useAuth } from '@/features/auth/use-auth'
import { cn } from '@/shared/lib/utils'

type SelectedMember = SkillSuiteMemberInput & { skillId: number; skillVersionId: number; displayName: string }
type CandidateScope = 'market' | 'namespace' | 'coordinate'

export function SuiteEditor({ namespace: routeNamespace, slug: routeSlug, version: routeVersion, mode = 'create' }: {
  namespace?: string
  slug?: string
  version?: string
  mode?: 'create' | 'edit' | 'new-version'
}) {
  const editing = mode === 'edit'
  const creatingVersion = mode === 'new-version'
  const bundleMode = creatingVersion ? 'UPDATE' : 'CREATE'
  const bundleCoordinate = creatingVersion && routeNamespace && routeSlug
    ? `@${routeNamespace}/${routeSlug}`
    : undefined
  const loadingSource = editing || creatingVersion
  const { t } = useTranslation()
  const { hasRole } = useAuth()
  const navigate = useNavigate()
  const { data: namespaces } = useMyNamespaces()
  const { data: existing, isLoading: isLoadingExisting, error: existingError } = useSuiteDetail(
    routeNamespace || '', routeSlug || '', routeVersion, loadingSource,
  )
  const [namespace, setNamespace] = useState(routeNamespace || '')
  const [slug, setSlug] = useState(routeSlug || '')
  const [displayName, setDisplayName] = useState('')
  const [summary, setSummary] = useState('')
  const [overview, setOverview] = useState('')
  const [version, setVersion] = useState(routeVersion || '1.0.0')
  const [visibility, setVisibility] = useState<SkillSuiteDraftInput['visibility']>('PUBLIC')
  const [changelog, setChangelog] = useState('')
  const [candidateInput, setCandidateInput] = useState('')
  const [candidateQuery, setCandidateQuery] = useState('')
  const [candidateScope, setCandidateScope] = useState<CandidateScope>('namespace')
  const [candidateSearchStarted, setCandidateSearchStarted] = useState(false)
  const [membersSettingsOpen, setMembersSettingsOpen] = useState(false)
  const [selected, setSelected] = useState<SelectedMember[]>([])
  const [entrySkillVersionId, setEntrySkillVersionId] = useState<number | null>(null)
  const [pendingVersionUpdate, setPendingVersionUpdate] = useState<SkillSuiteMemberCandidate | null>(null)
  const [namespaceRequired, setNamespaceRequired] = useState(false)
  const [slugRequired, setSlugRequired] = useState(false)
  const [nameRequired, setNameRequired] = useState(false)
  const [versionRequired, setVersionRequired] = useState(false)
  const [membersRequired, setMembersRequired] = useState(false)
  const slugInputRef = useRef<HTMLInputElement>(null)
  const nameInputRef = useRef<HTMLInputElement>(null)
  const versionInputRef = useRef<HTMLInputElement>(null)
  const [authoringMode, setAuthoringMode] = useState<'manual' | 'import'>(() => {
    if (editing) return 'manual'
    return hasStoredSuiteBundleOperation(bundleMode, bundleCoordinate) ? 'import' : 'manual'
  })
  const candidateInputTrimmed = candidateInput.trim()
  const backendCandidateQuery = useMemo(() => {
    if (candidateScope !== 'coordinate') return candidateQuery
    const raw = candidateQuery.trim().replace(/^@/, '')
    const [, skillSlug] = raw.split('/')
    return skillSlug || raw
  }, [candidateQuery, candidateScope])
  const { data: candidates, isLoading: isLoadingCandidates } = useSuiteMemberCandidates(
    namespace, visibility, backendCandidateQuery, Boolean(namespace) && candidateSearchStarted,
  )
  const createMutation = useCreateSuite()
  const createVersionMutation = useCreateSuiteVersion(existing?.id ?? 0)
  const updateMutation = useUpdateSuiteDraft(existing?.id ?? 0, existing?.versionId ?? 0)
  const { data: suiteLabels } = useSuiteLabels(
    existing?.namespace ?? '', existing?.slug ?? '', Boolean(existing && loadingSource),
  )

  const goBack = () => {
    if (routeNamespace && routeSlug) {
      navigate({
        to: `/dashboard/suites/${routeNamespace}/${encodeURIComponent(routeSlug)}`,
        search: { version: existing?.version ?? routeVersion },
      })
      return
    }
    navigate({ to: '/dashboard/suites' })
  }

  useEffect(() => {
    if (!namespace && namespaces?.length) setNamespace(namespaces[0].slug)
  }, [namespace, namespaces])

  useEffect(() => {
    if (!existing) return
    setNamespace(existing.namespace)
    setSlug(existing.slug)
    setDisplayName(existing.displayName)
    setSummary(existing.summary || '')
    setOverview(existing.overview || '')
    setVersion(creatingVersion ? '' : existing.version)
    setVisibility(existing.visibility)
    setSelected(existing.members.map((member) => ({
      namespace: member.namespace,
      slug: member.slug,
      version: member.version,
      skillId: member.skillId ?? -member.position - 1,
      skillVersionId: member.skillVersionId ?? -member.position - 1,
      displayName: `@${member.namespace}/${member.slug}`,
    })))
    setMembersRequired(false)
    setEntrySkillVersionId(existing.members.find((member) => member.entry)?.skillVersionId ?? null)
  }, [creatingVersion, existing])

  const selectedIds = useMemo(() => new Set(selected.map((member) => member.skillVersionId)), [selected])
  const selectedSkillIds = useMemo(() => new Set(selected.map((member) => member.skillId)), [selected])
  const filteredCandidates = useMemo(() => {
    const items = candidates ?? []
    if (candidateScope === 'namespace') {
      return items.filter((candidate) => candidate.namespace === namespace)
    }
    if (candidateScope === 'coordinate') {
      const raw = candidateQuery.trim().replace(/^@/, '')
      if (!raw) return items
      return items.filter((candidate) => `${candidate.namespace}/${candidate.slug}` === raw)
    }
    return items
  }, [candidateQuery, candidateScope, candidates, namespace])

  const selectedEntry = selected.find((member) => member.skillVersionId === entrySkillVersionId)
  const marketSearchRequiresKeyword = candidateScope === 'market' && candidateInputTrimmed.length === 0
  const runCandidateSearch = () => {
    if (marketSearchRequiresKeyword) return
    setCandidateSearchStarted(true)
    setCandidateQuery(candidateInputTrimmed)
  }

  const addCandidate = (candidate: SkillSuiteMemberCandidate) => {
    if (selectedIds.has(candidate.skillVersionId)) return
    setMembersRequired(false)
    setMembersSettingsOpen(true)
    setSelected((current) => {
      if (current.length === 0) setEntrySkillVersionId(candidate.skillVersionId)
      return [...current, {
        namespace: candidate.namespace,
        slug: candidate.slug,
        version: candidate.version,
        skillId: candidate.skillId,
        skillVersionId: candidate.skillVersionId,
        displayName: candidate.displayName,
      }]
    })
  }

  const applyCandidateUpdate = (candidate: SkillSuiteMemberCandidate) => {
    const previous = selected.find((member) => member.skillId === candidate.skillId)
    if (previous && entrySkillVersionId === previous.skillVersionId) {
      setEntrySkillVersionId(candidate.skillVersionId)
    }
    setSelected((current) => current.map((member) => {
      if (member.skillId !== candidate.skillId) return member
      return {
        namespace: candidate.namespace,
        slug: candidate.slug,
        version: candidate.version,
        skillId: candidate.skillId,
        skillVersionId: candidate.skillVersionId,
        displayName: candidate.displayName,
      }
    }))
  }

  const moveMember = (index: number, direction: -1 | 1) => {
    const target = index + direction
    if (target < 0 || target >= selected.length) return
    setSelected((current) => {
      const next = [...current]
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  const removeMember = (skillVersionId: number) => {
    setSelected((current) => current.filter((member) => member.skillVersionId !== skillVersionId))
    if (entrySkillVersionId === skillVersionId) setEntrySkillVersionId(null)
  }

  const save = async () => {
    if (!namespace) {
      setNamespaceRequired(true)
      toast.error(t('suite.namespaceRequired'))
      return
    }
    if (!slug.trim()) {
      setSlugRequired(true)
      slugInputRef.current?.focus()
      toast.error(t('suite.slugRequired'))
      return
    }
    if (!displayName.trim()) {
      setNameRequired(true)
      nameInputRef.current?.focus()
      toast.error(t('suite.nameRequired'))
      return
    }
    if (!version.trim()) {
      setVersionRequired(true)
      versionInputRef.current?.focus()
      toast.error(t('suite.versionRequired'))
      return
    }
    if (selected.length === 0) {
      setMembersRequired(true)
      toast.error(t('suite.membersRequired'))
      return
    }
    if (entrySkillVersionId === null) {
      toast.error(t('suite.entryRequired'))
      return
    }
    const members = selected.map(({ skillVersionId, namespace: memberNamespace, slug: memberSlug, version: memberVersion }) => ({
      skillVersionId,
      namespace: memberNamespace,
      slug: memberSlug,
      version: memberVersion,
    }))
    const entry = selected.find((member) => member.skillVersionId === entrySkillVersionId)
    if (!entry) {
      toast.error(t('suite.entryRequired'))
      return
    }
    const input = {
      namespace,
      slug: slug.trim(),
      displayName: displayName.trim(),
      summary: summary.trim() || undefined,
      overview: overview.trim() || undefined,
      version: version.trim(),
      visibility,
      changelog: changelog.trim() || undefined,
      entrySkill: {
        skillVersionId: entry.skillVersionId,
        namespace: entry.namespace,
        slug: entry.slug,
        version: entry.version,
      },
      members,
    }
    try {
      const result = editing
        ? await updateMutation.mutateAsync(input)
        : creatingVersion
          ? await createVersionMutation.mutateAsync(input)
          : await createMutation.mutateAsync(input)
      toast.success(editing ? t('suite.draftUpdated') : t('suite.draftCreated'))
      navigate({
        to: `/dashboard/suites/${result.namespace}/${encodeURIComponent(result.slug)}`,
        search: { version: result.version },
      })
    } catch (error) {
      toast.error(t('suite.saveFailed'), error instanceof Error ? error.message : '')
    }
  }

  if (loadingSource && isLoadingExisting) return <div className="h-64 animate-shimmer rounded-xl" />
  if (loadingSource && (!existing || existingError)) {
    return <Card className="mx-auto max-w-3xl p-8 text-center text-destructive">{t('suite.sourceLoadFailed')}</Card>
  }
  if (editing && existing && !existing.allowedActions.includes('EDIT')) {
    return <Card className="mx-auto max-w-3xl p-8 text-center text-destructive">{t('suite.editorAccessDenied')}</Card>
  }
  if (creatingVersion && existing && !existing.allowedActions.includes('CREATE_VERSION')) {
    return <Card className="mx-auto max-w-3xl p-8 text-center text-destructive">{t('suite.editorAccessDenied')}</Card>
  }

  return (
    <div className="mx-auto max-w-6xl space-y-5 pb-28 animate-fade-up">
      <SuiteWorkspaceHeader
        title={editing
          ? t('suite.editTitle')
          : creatingVersion
            ? t('suite.newVersionTitle')
            : t('suite.createTitle')}
        description={t('suite.editorDescription')}
        eyebrow={existing ? `@${existing.namespace}/${existing.slug}@${existing.version}` : undefined}
        backLabel={t(existing ? 'suite.bundle.backToSuite' : 'suite.management.backToSuites')}
        onBack={goBack}
      />

      {!editing ? (
        <div className="flex w-fit rounded-lg border bg-muted/30 p-1" role="tablist" aria-label={t('suite.authoringMode')}>
          <button
            type="button"
            role="tab"
            aria-selected={authoringMode === 'manual'}
            className={authoringMode === 'manual' ? 'rounded-md bg-background px-3 py-1.5 text-xs font-medium shadow-sm' : 'px-3 py-1.5 text-xs text-muted-foreground'}
            onClick={() => setAuthoringMode('manual')}
          >
            {t('suite.manualAuthoring')}
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={authoringMode === 'import'}
            className={authoringMode === 'import' ? 'rounded-md bg-background px-3 py-1.5 text-xs font-medium shadow-sm' : 'px-3 py-1.5 text-xs text-muted-foreground'}
            onClick={() => setAuthoringMode('import')}
          >
            {t('suite.localImport')}
          </button>
        </div>
      ) : null}

      {!editing && authoringMode === 'import' ? (
        <SuiteBundleImport
          key={`${bundleMode}:${bundleCoordinate ?? 'new'}`}
          expectedMode={bundleMode}
          expectedCoordinate={creatingVersion && existing ? bundleCoordinate : undefined}
          returnToSuite={creatingVersion && existing ? {
            namespace: existing.namespace,
            slug: existing.slug,
            version: existing.version,
          } : undefined}
        />
      ) : (
        <>

      <Card className="grid gap-4 p-4 md:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="suite-namespace">{t('suite.namespace')}</Label>
          <Select value={namespace} onValueChange={(value) => {
            setNamespace(value)
            if (value) setNamespaceRequired(false)
          }} disabled={loadingSource}>
            <SelectTrigger id="suite-namespace"><SelectValue placeholder={t('suite.selectNamespace')} /></SelectTrigger>
            <SelectContent>{namespaces?.map((item) => <SelectItem key={item.id} value={item.slug}>@{item.slug}</SelectItem>)}</SelectContent>
          </Select>
          {namespaceRequired ? <p role="alert" className="text-xs text-destructive">{t('suite.namespaceRequired')}</p> : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="suite-slug">{t('suite.slug')}</Label>
          <Input
            ref={slugInputRef}
            className="h-9"
            id="suite-slug"
            value={slug}
            disabled={loadingSource}
            aria-invalid={slugRequired || undefined}
            onChange={(event) => {
              setSlug(event.target.value)
              if (event.target.value.trim()) setSlugRequired(false)
            }}
            placeholder="marketing-workflow"
          />
          {slugRequired ? <p role="alert" className="text-xs text-destructive">{t('suite.slugRequired')}</p> : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="suite-name">{t('suite.name')}</Label>
          <Input
            ref={nameInputRef}
            className="h-9"
            id="suite-name"
            value={displayName}
            aria-invalid={nameRequired || undefined}
            onChange={(event) => {
              setDisplayName(event.target.value)
              if (event.target.value.trim()) setNameRequired(false)
            }}
            placeholder={t('suite.namePlaceholder')}
          />
          {nameRequired ? <p role="alert" className="text-xs text-destructive">{t('suite.nameRequired')}</p> : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="suite-version">{t('suite.version')}</Label>
          <Input
            ref={versionInputRef}
            id="suite-version"
            className="h-9"
            value={version}
            disabled={editing}
            aria-invalid={versionRequired || undefined}
            onChange={(event) => {
              setVersion(event.target.value)
              if (event.target.value.trim()) setVersionRequired(false)
            }}
            placeholder={creatingVersion ? t('suite.newVersionPlaceholder') : undefined}
          />
          {versionRequired ? <p role="alert" className="text-xs text-destructive">{t('suite.versionRequired')}</p> : null}
        </div>
        <div className="space-y-2">
          <Label htmlFor="suite-visibility">{t('suite.visibility')}</Label>
          <Select value={visibility} onValueChange={(value) => {
            if (value === 'PUBLIC' || value === 'NAMESPACE_ONLY' || value === 'PRIVATE') {
              setVisibility(value)
            }
          }}>
            <SelectTrigger id="suite-visibility"><SelectValue /></SelectTrigger>
            <SelectContent>
              <SelectItem value="PUBLIC">{t('suite.visibilityPublic')}</SelectItem>
              <SelectItem value="NAMESPACE_ONLY">{t('suite.visibilityNamespace')}</SelectItem>
              <SelectItem value="PRIVATE">{t('suite.visibilityPrivate')}</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2 md:col-span-2">
          <Label htmlFor="suite-summary">
            {t('suite.summary')}
            <span aria-hidden="true" className="ml-1 text-xs font-normal text-muted-foreground">
              · {t('suite.requiredForPublish')}
            </span>
          </Label>
          <Textarea
            id="suite-summary"
            aria-label={t('suite.summary')}
            value={summary}
            onChange={(event) => setSummary(event.target.value)}
            rows={2}
          />
        </div>
        <div className="space-y-2 md:col-span-2">
          <Label htmlFor="suite-overview">
            {t('suite.overview')}
            <span aria-hidden="true" className="ml-1 text-xs font-normal text-muted-foreground">
              · {t('suite.requiredForPublish')}
            </span>
          </Label>
          <p className="text-xs text-muted-foreground">{t('suite.overviewHint')}</p>
          <div className="grid gap-1.5 rounded-lg border border-border/60 bg-secondary/20 p-2.5 text-xs text-muted-foreground sm:grid-cols-2">
            {[
              'suite.overviewPromptScenario',
              'suite.overviewPromptPreparation',
              'suite.overviewPromptSequence',
              'suite.overviewPromptInputsOutputs',
              'suite.overviewPromptBoundaries',
            ].map(prompt => (
              <span key={prompt} className="flex gap-1.5">
                <CircleDot className="mt-0.5 h-3 w-3 shrink-0 text-primary" aria-hidden="true" />
                {t(prompt)}
              </span>
            ))}
          </div>
          <Textarea
            id="suite-overview"
            aria-label={t('suite.overview')}
            value={overview}
            onChange={(event) => setOverview(event.target.value)}
            maxLength={20000}
            rows={8}
          />
        </div>
        <div className="space-y-2 md:col-span-2">
          <Label htmlFor="suite-changelog">{t('suite.changelog')}</Label>
          <Textarea id="suite-changelog" value={changelog} onChange={(event) => setChangelog(event.target.value)} rows={2} />
        </div>
      </Card>

      <Card className="p-4" aria-live="polite">
        <h2 className="text-sm font-semibold">{t('suite.publishReadinessTitle')}</h2>
        <p className="mt-1 text-xs text-muted-foreground">{t('suite.publishReadinessDescription')}</p>
        <div className="mt-2.5 flex flex-wrap gap-2 text-xs">
          <span className={summary.trim() ? 'text-emerald-600' : 'text-amber-700 dark:text-amber-400'}>
            {summary.trim() ? t('suite.summaryComplete') : t('suite.summaryIncomplete')}
          </span>
          <span aria-hidden="true" className="text-muted-foreground">·</span>
          <span className={overview.trim() ? 'text-emerald-600' : 'text-amber-700 dark:text-amber-400'}>
            {overview.trim() ? t('suite.overviewComplete') : t('suite.overviewIncomplete')}
          </span>
        </div>
      </Card>

      {existing ? (
        <SuiteLabelPanel
          namespace={existing.namespace}
          slug={existing.slug}
          initialLabels={suiteLabels ?? []}
          canManage
          isSuperAdmin={hasRole('SUPER_ADMIN')}
          compact
        />
      ) : null}

      <Card className="overflow-hidden">
        <div className="border-b p-4">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold">{t('suite.selectSkills')}</h2>
              <p className="mt-1 text-xs text-muted-foreground">{t('suite.memberSearchDescription')}</p>
            </div>
            <span className="rounded-full bg-blue-500/10 px-3 py-1 text-xs text-blue-700 dark:text-blue-300">
              {t('suite.searchOnSubmitHint')}
            </span>
          </div>
          <div className="mt-4 flex flex-wrap gap-2" role="tablist" aria-label={t('suite.memberSearchScope')}>
            {([
              ['namespace', 'suite.scopeNamespace'],
              ['coordinate', 'suite.scopeCoordinate'],
              ['market', 'suite.scopeMarket'],
            ] as const).map(([scope, label]) => (
              <button
                key={scope}
                type="button"
                role="tab"
                aria-selected={candidateScope === scope}
                className={cn(
                  'rounded-md border px-3 py-1.5 text-xs transition-colors',
                  candidateScope === scope
                    ? 'border-primary/40 bg-primary/10 text-primary'
                    : 'border-border/60 bg-background text-muted-foreground hover:bg-muted',
                )}
                onClick={() => {
                  setCandidateScope(scope)
                  setCandidateSearchStarted(false)
                }}
              >
                {t(label)}
              </button>
            ))}
          </div>
          <div className="mt-3 grid gap-2 md:grid-cols-[minmax(0,1fr)_160px_auto]">
            <div className="relative">
              <Search className="absolute left-3 top-2.5 h-3.5 w-3.5 text-muted-foreground" />
              <Input
                className="h-9 pl-9"
                value={candidateInput}
                onChange={(event) => setCandidateInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') runCandidateSearch()
                }}
                placeholder={candidateScope === 'coordinate' ? '@global/skill-name' : t('suite.searchSkills')}
              />
            </div>
            <Select value={visibility} onValueChange={(value) => {
              if (value === 'PUBLIC' || value === 'NAMESPACE_ONLY' || value === 'PRIVATE') {
                setVisibility(value)
                setCandidateSearchStarted(false)
              }
            }}>
              <SelectTrigger><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="PUBLIC">{t('suite.visibilityPublic')}</SelectItem>
                <SelectItem value="NAMESPACE_ONLY">{t('suite.visibilityNamespace')}</SelectItem>
                <SelectItem value="PRIVATE">{t('suite.visibilityPrivate')}</SelectItem>
              </SelectContent>
            </Select>
            <Button size="sm" className="h-9 px-5" disabled={marketSearchRequiresKeyword} onClick={runCandidateSearch}>
              {t('suite.search')}
            </Button>
          </div>
        </div>

        <div className="min-h-[220px]">
          {isLoadingCandidates ? (
            <div className="m-4 h-28 animate-shimmer rounded-lg" />
          ) : !candidateSearchStarted ? (
            <div className="flex min-h-[210px] flex-col items-center justify-center px-6 py-8 text-center">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-muted">
                <Search className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
              </div>
              <h3 className="mt-3 text-sm font-semibold">{t('suite.memberSearchEmptyTitle')}</h3>
              <p className="mt-1.5 max-w-md text-xs leading-5 text-muted-foreground">{t('suite.memberSearchEmptyDescription')}</p>
            </div>
          ) : filteredCandidates.length === 0 ? (
            <p className="py-10 text-center text-sm text-muted-foreground">{t('suite.noCandidates')}</p>
          ) : (
            <div className="max-h-[420px] overflow-auto">
              <div className="sticky top-0 z-10 grid min-w-[760px] grid-cols-[minmax(220px,1.2fr)_minmax(180px,1fr)_120px_120px] border-b bg-muted/95 px-4 py-2 text-xs font-medium text-muted-foreground backdrop-blur">
                <span>{t('suite.memberSkillColumn')}</span>
                <span>{t('suite.memberDescriptionColumn')}</span>
                <span>{t('suite.memberVersionColumn')}</span>
                <span className="text-right">{t('suite.memberActionColumn')}</span>
              </div>
              {filteredCandidates.map((candidate) => {
                const alreadySelected = selectedIds.has(candidate.skillVersionId)
                const sameSkillDifferentVersion = selectedSkillIds.has(candidate.skillId) && !alreadySelected
                return (
                  <button
                    key={candidate.skillVersionId}
                    type="button"
                    className="grid min-w-[760px] w-full grid-cols-[minmax(220px,1.2fr)_minmax(180px,1fr)_120px_120px] items-center gap-3 border-b px-4 py-3 text-left transition-colors hover:bg-muted/60 disabled:cursor-not-allowed disabled:opacity-55"
                    disabled={alreadySelected}
                    onClick={() => sameSkillDifferentVersion
                      ? setPendingVersionUpdate(candidate)
                      : addCandidate(candidate)}
                  >
                    <span className="min-w-0">
                      <span className="block truncate text-sm font-medium">{candidate.displayName}</span>
                      <span className="block truncate text-xs text-muted-foreground">@{candidate.namespace}/{candidate.slug}</span>
                    </span>
                    <span className="min-w-0 text-xs text-muted-foreground">
                      <span className="inline-flex rounded-full bg-purple-500/10 px-2 py-0.5 text-purple-700 dark:text-purple-300">{candidate.visibility}</span>
                      {candidate.recommended ? <span className="ml-1 inline-flex rounded-full bg-emerald-500/10 px-2 py-0.5 text-emerald-700 dark:text-emerald-300">{t('suite.recommendedCandidate')}</span> : null}
                    </span>
                    <span className="font-mono text-xs">v{candidate.version}</span>
                    <span className="flex justify-end">
                      <span className={sameSkillDifferentVersion ? 'text-xs text-primary' : 'inline-flex items-center gap-1 rounded-md border border-primary/30 px-2 py-1 text-xs text-primary'}>
                        {sameSkillDifferentVersion ? t('suite.updatePinnedVersion') : <><Plus className="h-3.5 w-3.5" aria-hidden="true" />{t('suite.addMember')}</>}
                      </span>
                    </span>
                  </button>
                )
              })}
            </div>
          )}
        </div>
      </Card>

      {membersSettingsOpen || membersRequired ? (
        <Card className={membersRequired ? 'border-destructive/50 p-4' : 'p-4'}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h2 className="text-sm font-semibold">{t('suite.selectedMembers', { count: selected.length })}</h2>
              <p className="mt-1 text-xs text-muted-foreground">{t('suite.selectedMembersDescription')}</p>
            </div>
            <Button size="sm" variant="ghost" onClick={() => setMembersSettingsOpen(false)}>{t('suite.collapseMembers')}</Button>
          </div>
          {membersRequired ? <p role="alert" className="mt-2 text-xs text-destructive">{t('suite.membersRequired')}</p> : null}
          <div className="mt-3 space-y-1.5" role="radiogroup" aria-label={t('suite.entrySkill')}>
            {selected.map((member, index) => {
              const isEntry = entrySkillVersionId === member.skillVersionId
              return (
                <div key={member.skillVersionId} className={cn('rounded-lg border p-2.5', isEntry && 'border-amber-300 bg-amber-50/60 dark:bg-amber-950/20')}>
                  <div className="flex flex-wrap items-center gap-2">
                    <button type="button" className="min-w-[220px] flex-1 text-left" onClick={() => setEntrySkillVersionId(member.skillVersionId)}>
                      <span className="block truncate text-sm font-medium">{member.displayName}</span>
                      <span className="text-xs text-muted-foreground">@{member.namespace}/{member.slug}@{member.version}</span>
                    </button>
                    {isEntry ? <span className="rounded-full bg-amber-200/80 px-2 py-0.5 text-xs text-amber-900">{t('suite.entrySkill')}</span> : null}
                    <Button className="h-7 w-7 p-0" aria-label={t('suite.moveMemberUp', { name: member.displayName })} variant="outline" size="sm" disabled={index === 0} onClick={() => moveMember(index, -1)}><ArrowUp className="h-3.5 w-3.5" aria-hidden="true" /></Button>
                    <Button className="h-7 w-7 p-0" aria-label={t('suite.moveMemberDown', { name: member.displayName })} variant="outline" size="sm" disabled={index === selected.length - 1} onClick={() => moveMember(index, 1)}><ArrowDown className="h-3.5 w-3.5" aria-hidden="true" /></Button>
                    <Button className="h-7 w-7 p-0" aria-label={t('suite.removeMember', { name: member.displayName })} variant="ghost" size="sm" onClick={() => removeMember(member.skillVersionId)}><Trash2 className="h-3.5 w-3.5" aria-hidden="true" /></Button>
                  </div>
                  <label className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
                    <input type="radio" name="suite-entry-skill" aria-label={t('suite.setEntryFor', { name: member.displayName })} checked={isEntry} onChange={() => setEntrySkillVersionId(member.skillVersionId)} />
                    {t('suite.setEntry')}
                  </label>
                </div>
              )
            })}
            {selected.length === 0 ? <p className="py-8 text-center text-sm text-muted-foreground">{t('suite.addMemberHint')}</p> : null}
          </div>
        </Card>
      ) : null}

      <div className="sticky bottom-0 z-20 -mx-4 border-t bg-background/95 px-4 py-3 shadow-[0_-8px_24px_-20px_rgb(0_0_0/0.45)] backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3">
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 text-sm font-medium">
              <span>{t('suite.selectedMembers', { count: selected.length })}</span>
              {selectedEntry ? <span className="rounded-full bg-amber-200/80 px-2 py-0.5 text-xs text-amber-900">{t('suite.entrySkill')}: {selectedEntry.displayName}</span> : null}
            </div>
            <div className="mt-2 flex gap-2 overflow-x-auto pb-1">
              {selected.length === 0 ? (
                <span className="text-xs text-muted-foreground">{t('suite.addMemberHint')}</span>
              ) : selected.map((member) => (
                <span key={member.skillVersionId} className={cn('inline-flex shrink-0 items-center gap-2 rounded-md border px-2 py-1 text-xs', entrySkillVersionId === member.skillVersionId && 'border-amber-300 bg-amber-50 text-amber-950')}>
                  <span className="max-w-56 truncate">@{member.namespace}/{member.slug}@{member.version}</span>
                  <button type="button" aria-label={t('suite.removeMember', { name: member.displayName })} onClick={() => removeMember(member.skillVersionId)}>
                    <X className="h-3 w-3" aria-hidden="true" />
                  </button>
                </span>
              ))}
            </div>
          </div>
          <Button size="sm" variant="outline" onClick={() => setMembersSettingsOpen((open) => !open)}>
            {membersSettingsOpen ? t('suite.collapseMembers') : t('suite.expandMembers')}
          </Button>
          <Button size="sm" variant="outline" onClick={goBack}>{t('suite.cancel')}</Button>
          <Button
            size="sm"
            disabled={createMutation.isPending || createVersionMutation.isPending || updateMutation.isPending}
            onClick={save}
          >{t('suite.saveDraft')}</Button>
        </div>
      </div>

      <ConfirmDialog
        open={pendingVersionUpdate !== null}
        onOpenChange={(open) => { if (!open) setPendingVersionUpdate(null) }}
        title={t('suite.confirmVersionUpdateTitle')}
        description={pendingVersionUpdate ? t('suite.confirmVersionUpdateDescription', {
          coordinate: `@${pendingVersionUpdate.namespace}/${pendingVersionUpdate.slug}`,
          from: selected.find((member) => member.skillId === pendingVersionUpdate.skillId)?.version,
          to: pendingVersionUpdate.version,
        }) : undefined}
        confirmText={t('suite.confirmVersionUpdate')}
        onConfirm={() => {
          if (pendingVersionUpdate) applyCandidateUpdate(pendingVersionUpdate)
        }}
      />
        </>
      )}
    </div>
  )
}

export function SuiteCreatePage() {
  return <SuiteEditor />
}

export function SuiteEditPage() {
  const { namespace, slug } = useParams({ from: '/dashboard/suites/$namespace/$slug/edit' })
  const { version } = useSearch({ from: '/dashboard/suites/$namespace/$slug/edit' })
  return <SuiteEditor namespace={namespace} slug={slug} version={version} mode="edit" />
}

export function SuiteVersionCreatePage() {
  const { namespace, slug } = useParams({ from: '/dashboard/suites/$namespace/$slug/new-version' })
  const { sourceVersion } = useSearch({ from: '/dashboard/suites/$namespace/$slug/new-version' })
  return <SuiteEditor namespace={namespace} slug={slug} version={sourceVersion} mode="new-version" />
}
