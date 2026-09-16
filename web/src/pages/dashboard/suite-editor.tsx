import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowDown, ArrowUp, Search, Trash2 } from 'lucide-react'
import type { SkillSuiteDraftInput, SkillSuiteMemberCandidate, SkillSuiteMemberInput } from '@/api/types'
import {
  useCreateSuite,
  useCreateSuiteVersion,
  useSuiteDetail,
  useSuiteMemberCandidates,
  useUpdateSuiteDraft,
} from '@/shared/hooks/use-suite-queries'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { useDebounce } from '@/shared/hooks/use-debounce'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { toast } from '@/shared/lib/toast'

type SelectedMember = SkillSuiteMemberInput & { skillId: number; skillVersionId: number; displayName: string }

export function SuiteEditor({ namespace: routeNamespace, slug: routeSlug, version: routeVersion, mode = 'create' }: {
  namespace?: string
  slug?: string
  version?: string
  mode?: 'create' | 'edit' | 'new-version'
}) {
  const editing = mode === 'edit'
  const creatingVersion = mode === 'new-version'
  const loadingSource = editing || creatingVersion
  const { t } = useTranslation()
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
  const [candidateQuery, setCandidateQuery] = useState('')
  const [selected, setSelected] = useState<SelectedMember[]>([])
  const [entrySkillVersionId, setEntrySkillVersionId] = useState<number | null>(null)
  const [pendingVersionUpdate, setPendingVersionUpdate] = useState<SkillSuiteMemberCandidate | null>(null)
  const debouncedQuery = useDebounce(candidateQuery.trim(), 250)
  const { data: candidates, isLoading: isLoadingCandidates } = useSuiteMemberCandidates(
    namespace, visibility, debouncedQuery, Boolean(namespace),
  )
  const createMutation = useCreateSuite()
  const createVersionMutation = useCreateSuiteVersion(existing?.id ?? 0)
  const updateMutation = useUpdateSuiteDraft(existing?.id ?? 0, existing?.versionId ?? 0)

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
    setEntrySkillVersionId(existing.members.find((member) => member.entry)?.skillVersionId ?? null)
  }, [creatingVersion, existing])

  const selectedIds = useMemo(() => new Set(selected.map((member) => member.skillVersionId)), [selected])
  const selectedSkillIds = useMemo(() => new Set(selected.map((member) => member.skillId)), [selected])

  const addCandidate = (candidate: SkillSuiteMemberCandidate) => {
    if (selectedIds.has(candidate.skillVersionId)) return
    setSelected((current) => [...current, {
      namespace: candidate.namespace,
      slug: candidate.slug,
      version: candidate.version,
      skillId: candidate.skillId,
      skillVersionId: candidate.skillVersionId,
      displayName: candidate.displayName,
    }])
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
    if (!namespace || !slug.trim() || !displayName.trim() || !version.trim() || selected.length === 0) {
      toast.error(t('suite.validationRequired'))
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
        to: `/suite/${result.namespace}/${encodeURIComponent(result.slug)}`,
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
    <div className="mx-auto max-w-5xl space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={editing
          ? t('suite.editTitle')
          : creatingVersion
            ? t('suite.newVersionTitle')
            : t('suite.createTitle')}
        subtitle={t('suite.editorDescription')}
      />

      <Card className="grid gap-5 p-6 md:grid-cols-2">
        <div className="space-y-2">
          <Label htmlFor="suite-namespace">{t('suite.namespace')}</Label>
          <Select value={namespace} onValueChange={setNamespace} disabled={loadingSource}>
            <SelectTrigger id="suite-namespace"><SelectValue placeholder={t('suite.selectNamespace')} /></SelectTrigger>
            <SelectContent>{namespaces?.map((item) => <SelectItem key={item.id} value={item.slug}>@{item.slug}</SelectItem>)}</SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="suite-slug">{t('suite.slug')}</Label>
          <Input id="suite-slug" value={slug} disabled={loadingSource} onChange={(event) => setSlug(event.target.value)} placeholder="marketing-workflow" />
        </div>
        <div className="space-y-2">
          <Label htmlFor="suite-name">{t('suite.name')}</Label>
          <Input id="suite-name" value={displayName} onChange={(event) => setDisplayName(event.target.value)} placeholder={t('suite.namePlaceholder')} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="suite-version">{t('suite.version')}</Label>
          <Input id="suite-version" value={version} disabled={editing} onChange={(event) => setVersion(event.target.value)} placeholder={creatingVersion ? t('suite.newVersionPlaceholder') : undefined} />
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
          <Label htmlFor="suite-summary">{t('suite.summary')}</Label>
          <Textarea id="suite-summary" value={summary} onChange={(event) => setSummary(event.target.value)} rows={3} />
        </div>
        <div className="space-y-2 md:col-span-2">
          <Label htmlFor="suite-overview">{t('suite.overview')}</Label>
          <p className="text-xs text-muted-foreground">{t('suite.overviewHint')}</p>
          <Textarea
            id="suite-overview"
            value={overview}
            onChange={(event) => setOverview(event.target.value)}
            maxLength={20000}
            rows={10}
          />
        </div>
        <div className="space-y-2 md:col-span-2">
          <Label htmlFor="suite-changelog">{t('suite.changelog')}</Label>
          <Textarea id="suite-changelog" value={changelog} onChange={(event) => setChangelog(event.target.value)} rows={2} />
        </div>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-6">
          <h2 className="font-semibold">{t('suite.selectSkills')}</h2>
          <div className="relative mt-4">
            <Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground" />
            <Input className="pl-9" value={candidateQuery} onChange={(event) => setCandidateQuery(event.target.value)} placeholder={t('suite.searchSkills')} />
          </div>
          <div className="mt-4 max-h-96 space-y-2 overflow-y-auto">
            {isLoadingCandidates ? <div className="h-16 animate-shimmer rounded-lg" /> : candidates?.map((candidate) => (
              <button
                key={candidate.skillVersionId}
                type="button"
                className="flex w-full items-center justify-between rounded-lg border p-3 text-left hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
                disabled={selectedIds.has(candidate.skillVersionId)}
                onClick={() => selectedSkillIds.has(candidate.skillId)
                  ? setPendingVersionUpdate(candidate)
                  : addCandidate(candidate)}
              >
                <span><span className="block font-medium">{candidate.displayName}</span><span className="text-xs text-muted-foreground">@{candidate.namespace}/{candidate.slug}</span></span>
                <span className="text-right font-mono text-xs">
                  v{candidate.version}
                  {selectedSkillIds.has(candidate.skillId) && !selectedIds.has(candidate.skillVersionId)
                    ? <span className="mt-1 block font-sans text-primary">{t('suite.updatePinnedVersion')}</span>
                    : null}
                </span>
              </button>
            ))}
            {!isLoadingCandidates && candidates?.length === 0 ? <p className="py-8 text-center text-sm text-muted-foreground">{t('suite.noCandidates')}</p> : null}
          </div>
        </Card>

        <Card className="p-6">
          <h2 className="font-semibold">{t('suite.selectedMembers', { count: selected.length })}</h2>
          <div className="mt-4 space-y-2" role="radiogroup" aria-label={t('suite.entrySkill')}>
            {selected.map((member, index) => (
              <div key={member.skillVersionId} className="rounded-lg border p-3">
                <div className="flex items-center gap-2">
                  <button type="button" className="min-w-0 flex-1 text-left" onClick={() => setEntrySkillVersionId(member.skillVersionId)}>
                    <span className="block truncate font-medium">{member.displayName}</span>
                    <span className="text-xs text-muted-foreground">@{member.namespace}/{member.slug}@{member.version}</span>
                  </button>
                  <Button aria-label={t('suite.moveMemberUp', { name: member.displayName })} variant="outline" size="sm" disabled={index === 0} onClick={() => moveMember(index, -1)}><ArrowUp className="h-4 w-4" aria-hidden="true" /></Button>
                  <Button aria-label={t('suite.moveMemberDown', { name: member.displayName })} variant="outline" size="sm" disabled={index === selected.length - 1} onClick={() => moveMember(index, 1)}><ArrowDown className="h-4 w-4" aria-hidden="true" /></Button>
                  <Button aria-label={t('suite.removeMember', { name: member.displayName })} variant="ghost" size="sm" onClick={() => removeMember(member.skillVersionId)}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
                </div>
                <label className="mt-2 flex items-center gap-2 text-xs text-muted-foreground">
                  <input type="radio" name="suite-entry-skill" aria-label={t('suite.setEntryFor', { name: member.displayName })} checked={entrySkillVersionId === member.skillVersionId} onChange={() => setEntrySkillVersionId(member.skillVersionId)} />
                  {t('suite.setEntry')}
                </label>
              </div>
            ))}
            {selected.length === 0 ? <p className="py-8 text-center text-sm text-muted-foreground">{t('suite.addMemberHint')}</p> : null}
          </div>
        </Card>
      </div>

      <div className="flex justify-end gap-3">
        <Button variant="outline" onClick={() => window.history.back()}>{t('suite.cancel')}</Button>
        <Button
          disabled={createMutation.isPending || createVersionMutation.isPending || updateMutation.isPending}
          onClick={save}
        >{t('suite.saveDraft')}</Button>
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
