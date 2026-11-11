import { useEffect, useId, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { UploadZone } from '@/features/publish/upload-zone'
import { packageFolderAsZip } from '@/features/publish/folder-zip'
import {
  extractPrecheckWarnings,
  isFrontmatterFailureMessage,
  isPrecheckConfirmationMessage,
  isPrecheckFailureMessage,
  isVersionExistsMessage,
} from '@/features/publish/publish-error-utils'
import { normalizePublishPrefill } from '@/features/publish/publish-prefill'
import { Button } from '@/shared/ui/button'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  normalizeSelectValue,
} from '@/shared/ui/select'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import { Label } from '@/shared/ui/label'
import { stripMarkdownFrontmatter } from '@/features/skill/markdown-frontmatter'
import { PublishResultPanel } from '@/features/publish/publish-result-panel'
import { classifyPublishFailure, type PublishSubmissionState } from '@/features/publish/publish-result'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { readPackageOverview } from '@/features/publish/package-overview'
import { PresentationEditor } from '@/features/publish/presentation-editor'
import { presentationPayload, publishedOverrides, type PresentationSource } from '@/features/publish/presentation-source'
import { Dialog, DialogContent, DialogTitle } from '@/shared/ui/dialog'
import { Pencil, RotateCcw } from 'lucide-react'
import { SkillAvatarEditor, KEEP_AVATAR, type AvatarEdit } from '@/features/skill/skill-avatar-editor'
import { SkillAvatarManager } from '@/features/skill/skill-avatar-manager'
import { previewSkillPackage, usePublishSkill, useSkillDetail, useSkillVersionDetail } from '@/shared/hooks/use-skill-queries'
import { getSkillDetailQueryKey } from '@/shared/hooks/query-keys'
import { useMyNamespaces } from '@/shared/hooks/use-namespace-queries'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { navigateAfterOverlays } from '@/shared/lib/navigate-after-overlays'
import { toast } from '@/shared/lib/toast'
import { refreshSkillPermissionsAfterForbidden } from '@/features/skill/refresh-skill-permissions'
import { ApiError, skillAvatarApi } from '@/api/client'

const EMPTY_NAMESPACE_VALUE = '__select_namespace__'

export function PublishPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/dashboard/publish' })
  const prefill = normalizePublishPrefill(search)
  const updateSlug = typeof search.updateSkill === 'string' ? search.updateSkill : prefill.resubmitSkill
  const isUpdate = !!search.updateSkill && !prefill.resubmitSkill
  const queryClient = useQueryClient()
  const submitting = useRef(false)
  const [isSavingAvatar, setIsSavingAvatar] = useState(false)
  const [avatarEdit, setAvatarEdit] = useState<AvatarEdit>(KEEP_AVATAR)
  const previewId = useId()
  const [fileRevision, setFileRevision] = useState(0)
  const [displayName, setDisplayName] = useState('')
  const [shortDescription, setShortDescription] = useState('')
  const [overview, setOverview] = useState('')
  const [editorOpen, setEditorOpen] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [submissionState, setSubmissionState] = useState<PublishSubmissionState | null>(null)
  const [shortSource, setShortSource] = useState<PresentationSource>('package')
  const [overviewSource, setOverviewSource] = useState<PresentationSource>('package')
  const [attempted, setAttempted] = useState(false)
  const [viewOverview, setViewOverview] = useState(false)
  const [restoreField, setRestoreField] = useState<'short' | 'overview' | null>(null)
  const editorOriginal = useRef('')
  const restoredFocus = useRef<string | null>(null)
  const restoreOpener = useRef<HTMLButtonElement | null>(null)
  const initialized = useRef('')
  const sourceTouched = useRef({short:false,overview:false})
  const resourceIdentity = `${prefill.namespace}/${updateSlug}`
  const formIdentity = useRef(resourceIdentity)
  const displayNameEdited = useRef(false)
  const shortDescriptionEdited = useRef(false)
  const overviewEdited = useRef(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [namespaceSlug, setNamespaceSlug] = useState<string>(prefill.namespace)
  const [visibility, setVisibility] = useState<string>(prefill.visibility)
  const [warningDialogOpen, setWarningDialogOpen] = useState(false)
  const [precheckWarnings, setPrecheckWarnings] = useState<string[]>([])
  const [isPackaging, setIsPackaging] = useState(false)
  const [folderError, setFolderError] = useState('')
  const selectionGeneration = useRef(0)
  // Route identity is read during render. Effects must not hydrate cached data
  // from the previous namespace while its local form state is being reset.
  const resourceReady = formIdentity.current === resourceIdentity && (!updateSlug || namespaceSlug === prefill.namespace)

  useEffect(() => {
    const identity = `${prefill.namespace}/${updateSlug}`
    if (formIdentity.current !== identity) {
      selectionGeneration.current += 1
      formIdentity.current = identity
      initialized.current = ''
      sourceTouched.current = { short: false, overview: false }
      restoredFocus.current = null
      restoreOpener.current = null
      displayNameEdited.current = false
      shortDescriptionEdited.current = false
      overviewEdited.current = false
      setIsPackaging(false)
      setFolderError('')
      setSelectedFile(null)
      setFileRevision(value => value + 1)
      setDisplayName('')
      setShortDescription('')
      setOverview('')
      setShortSource('package')
      setOverviewSource('package')
      setAttempted(false)
      setSubmissionState(null)
      setEditorOpen(false)
      setConfirmOpen(false)
      setRestoreField(null)
      setViewOverview(false)
      setWarningDialogOpen(false)
      setPrecheckWarnings([])
    }
    setNamespaceSlug(prefill.namespace)
    setVisibility(prefill.visibility)
    setAvatarEdit(KEEP_AVATAR)
  }, [prefill.namespace, prefill.visibility, updateSlug])

  const { data: namespaces, isLoading: isLoadingNamespaces } = useMyNamespaces()
  const publishMutation = usePublishSkill()
  const { data: updateTarget } = useSkillDetail(namespaceSlug, updateSlug ?? '', resourceReady && !!namespaceSlug && !!updateSlug)
  const ownerReadOnly = updateTarget?.ownerReadOnly === true
  useEffect(() => { if (ownerReadOnly) { setEditorOpen(false); setConfirmOpen(false); setWarningDialogOpen(false); setRestoreField(null); setViewOverview(false) } }, [ownerReadOnly])
  const packagePreview = useQuery({
    queryKey: ['skill-package-preview', previewId, resourceIdentity, fileRevision],
    queryFn: () => previewSkillPackage(selectedFile!),
    enabled: resourceReady && selectedFile !== null,
    retry: false,
    gcTime: 0,
  })
  const packageOverview = useQuery({
    queryKey: ['skill-package-overview', previewId, resourceIdentity, fileRevision],
    queryFn: ({ signal }) => readPackageOverview(selectedFile!, signal),
    enabled: resourceReady && selectedFile !== null && packagePreview.isSuccess && !packagePreview.data?.overview,
    retry: false,
    gcTime: 0,
  })
  const packageShort = packagePreview.data?.shortDescription ?? packagePreview.data?.description ?? ''
  const packageBody = packagePreview.data?.overview || packageOverview.data?.content || ''
  const readingPackageOverview = selectedFile !== null && packagePreview.isSuccess && !packagePreview.data?.overview && packageOverview.isPending
  const readingOverview = overviewSource === 'package' && readingPackageOverview
  const overviewPreviewFailed = overviewSource === 'package' && (packageOverview.isError || !!packageOverview.data?.error)
  const previewOverview = resourceReady ? (overviewSource === 'custom' ? overview : packageBody) : ''
  const previewShortDescription = resourceReady ? (shortSource === 'custom' ? shortDescription : packageShort) : ''
  const shortInvalid = !previewShortDescription.trim() || previewShortDescription.length > 200
  const overviewInvalid = !stripMarkdownFrontmatter(previewOverview).trim() || previewOverview.length > 20000
  const { data: publishedDetail, isLoading: readingPublished, isError: publishedFailed, refetch: retryPublished } = useSkillVersionDetail(namespaceSlug, updateSlug ?? '', updateTarget?.publishedVersion?.version, resourceReady && !!updateSlug && !!updateTarget?.publishedVersion?.version)
  useEffect(() => {
    const key = `${namespaceSlug}/${updateSlug}/${publishedDetail?.version}`
    if (!resourceReady || !publishedDetail || initialized.current === key) return
    initialized.current = key
    const previous = publishedOverrides(publishedDetail.parsedMetadataJson)
    if (!sourceTouched.current.short && !shortDescriptionEdited.current && previous.short !== undefined) { shortDescriptionEdited.current = true; setShortDescription(previous.short); setShortSource('custom') }
    if (!sourceTouched.current.overview && !overviewEdited.current && previous.overview !== undefined) { overviewEdited.current = true; setOverview(previous.overview); setOverviewSource('custom') }
  }, [namespaceSlug, updateSlug, publishedDetail, resourceReady])
  useEffect(() => {
    if (resourceReady && selectedFile && packagePreview.data && !displayNameEdited.current) {
      setDisplayName(packagePreview.data.displayName ?? packagePreview.data.name ?? '')
    }

  }, [packagePreview.data, resourceReady, selectedFile])
  const avatarTarget = useQuery({ queryKey: ['skill-avatar', namespaceSlug, updateSlug], queryFn: () => skillAvatarApi.status(namespaceSlug, updateSlug), enabled: resourceReady && !!namespaceSlug && !!updateSlug })
  const avatarUnavailable = !!updateSlug && (avatarTarget.isLoading || avatarTarget.isError)
  const isBusy = publishMutation.isPending || isSavingAvatar
  const avatarOnly = isUpdate && !selectedFile
  const canSubmit = resourceReady && !ownerReadOnly && !isPackaging && (!selectedFile || packagePreview.isSuccess) && displayName.trim().length <= 200 && (avatarOnly || (!shortInvalid && !overviewInvalid && !readingOverview && !readingPublished && !publishedFailed)) && !!namespaceSlug && avatarEdit.ready && !avatarUnavailable && !isBusy && (selectedFile !== null || (isUpdate && avatarEdit.action !== 'KEEP'))
  const selectedNamespace = namespaces?.find((ns) => ns.slug === namespaceSlug)
  const namespaceOnlyLabel = selectedNamespace?.type === 'GLOBAL'
    ? t('publish.visibilityOptions.loggedInUsersOnly')
    : t('publish.visibilityOptions.namespaceOnly')



  const handleRemoveSelectedFile = () => {
    selectionGeneration.current += 1
    setFolderError('')
    setSelectedFile(null)
    setPrecheckWarnings([])
    setWarningDialogOpen(false)
  }

  const handleFileSelect = (file: File | null) => {
    selectionGeneration.current += 1
    setFolderError('')
    setSelectedFile(file)
    setFileRevision(value => value + 1)
    setPrecheckWarnings([])
    setWarningDialogOpen(false)
  }

  const handleFolderSelect = async (files: File[]) => {
    const generation = ++selectionGeneration.current
    const identity = formIdentity.current
    setFolderError('')
    setIsPackaging(true)
    try {
      const zip = await packageFolderAsZip(files)
      if (generation !== selectionGeneration.current || identity !== formIdentity.current) return
      setIsPackaging(false)
      handleFileSelect(zip)
    } catch {
      if (generation === selectionGeneration.current && identity === formIdentity.current) setFolderError(t('publish.folderPackagingFailed'))
    } finally {
      if (identity === formIdentity.current && generation === selectionGeneration.current) setIsPackaging(false)
    }
  }

  const publishSkill = async (confirmWarnings = false) => {
    if (submitting.current) return
    if (!canSubmit) {
      toast.error(t(ownerReadOnly ? 'mySkills.ownerReadOnly' : 'publish.selectRequired'))
      return
    }

    submitting.current = true
    setWarningDialogOpen(false)
    setConfirmOpen(false)
    setEditorOpen(false)
    setViewOverview(false)
    setRestoreField(null)
    if (!avatarOnly) setSubmissionState({ kind: 'submitting', label: displayName, version: packagePreview.data?.version })
    try {
      if (avatarOnly) {
        setIsSavingAvatar(true)
        if (avatarEdit.action === 'REMOVE') await skillAvatarApi.delete(namespaceSlug, updateSlug)
        else await skillAvatarApi.submit(namespaceSlug, updateSlug, avatarEdit.uploadId!)
        await Promise.all([
          queryClient.invalidateQueries({ queryKey: ['skills'], refetchType: avatarEdit.action === 'REMOVE' ? 'all' : 'active' }),
          queryClient.invalidateQueries({ queryKey: ['skill-avatar', namespaceSlug, updateSlug] }),
          queryClient.invalidateQueries({ queryKey: ['reviews'] }),
        ])
        toast.success(t('publish.avatarSaved'), t(avatarEdit.action === 'REMOVE' ? 'skillAvatar.removedNotice' : 'skillAvatar.submittedNotice'))
        navigateAfterOverlays(() => { navigate({ to: '/dashboard/skills' }) })
        return
      }
      const result = await publishMutation.mutateAsync({
        namespace: namespaceSlug,
        file: selectedFile!,
        visibility,
        confirmWarnings,
        avatarAction: avatarEdit.action,
        avatarUploadId: avatarEdit.uploadId,
        expectedSlug: updateSlug || undefined,
        displayName,
        ...presentationPayload(shortSource, overviewSource, shortDescription, overview),
      })
      setPrecheckWarnings([])
      setWarningDialogOpen(false)
      setSubmissionState({ kind: 'accepted', result, hadPublishedVersion: !!updateTarget?.publishedVersion })
    } catch (error) {
      // A package rejection may also reflect changed membership. Re-read the
      // server state instead of inferring permissions from its localized message.
      if (updateSlug && !avatarOnly && error instanceof ApiError && error.status === 400) {
        await queryClient.invalidateQueries({ queryKey: getSkillDetailQueryKey(namespaceSlug, updateSlug ?? '') })
      }
      // Avatar-only updates call the API directly rather than a mutation.
      if (avatarOnly) await refreshSkillPermissionsAfterForbidden(queryClient, error)
      if (!avatarOnly) { const failure = classifyPublishFailure(error, packagePreview.data?.version); if (failure) { setSubmissionState(failure); return } setSubmissionState(null) }
      if (error instanceof ApiError && error.status === 408) {
        toast.error(t('publish.timeoutTitle'), t('publish.timeoutDescription'))
        return
      }

      if (error instanceof ApiError && isVersionExistsMessage(error.serverMessage || error.message)) {
        toast.error(
          t('publish.versionExistsTitle'),
          t('publish.versionExistsDescription'),
        )
        return
      }

      if (error instanceof ApiError && isPrecheckConfirmationMessage(error.serverMessage || error.message)) {
        setPrecheckWarnings(extractPrecheckWarnings(error.serverMessage || error.message))
        setWarningDialogOpen(true)
        return
      }

      if (error instanceof ApiError && isPrecheckFailureMessage(error.serverMessage || error.message)) {
        toast.error(
          t('publish.precheckFailedTitle'),
          error.serverMessage || t('publish.precheckFailedDescription'),
        )
        return
      }

      if (error instanceof ApiError && isFrontmatterFailureMessage(error.serverMessage || error.message)) {
        toast.error(
          t('publish.frontmatterFailedTitle'),
          error.serverMessage || t('publish.frontmatterFailedDescription'),
        )
        return
      }

      toast.error(t('publish.error'), error instanceof Error ? error.message : '')
    } finally {
      submitting.current = false
      setIsSavingAvatar(false)
    }
  }

  const handlePublish = async () => {
    await publishSkill(false)
  }

  const openOverviewEditor = () => {
    editorOriginal.current = previewOverview
    setEditorOpen(true)
  }

  const restorePackageContent = () => {
    if (!restoreField || ownerReadOnly || isBusy || !selectedFile || !packagePreview.isSuccess) return
    sourceTouched.current[restoreField] = true
    if (restoreField === 'short') {
      setShortSource('package')
      setShortDescription('')
      shortDescriptionEdited.current = false
    } else {
      setOverviewSource('package')
      setOverview('')
      overviewEdited.current = false
    }
    restoredFocus.current = restoreField === 'short' ? 'skill-short-description' : 'skill-overview-label'
    setRestoreField(null)
  }

  const focusField = (id: string) => {
    const field = document.getElementById(id)
    field?.scrollIntoView?.({ block: 'center' })
    field?.focus()
  }

  const missingFields = [
    { id: 'publish-package-section', invalid: !selectedFile, label: 'publish.file' },
    { id: 'namespace', invalid: !namespaceSlug, label: 'publish.namespace' },
    { id: 'skill-short-description', invalid: shortInvalid, label: 'publish.shortDescription' },
    { id: 'skill-overview-label', invalid: overviewInvalid, label: 'publish.overview' },
  ].filter(field => field.invalid)

  return (
    <div className="mx-auto w-full max-w-[1200px] animate-fade-up">
      {submissionState && <PublishResultPanel
        state={submissionState}
        onReturnToForm={() => {
          setSubmissionState(null)
          requestAnimationFrame(() => {
            const action = document.getElementById('publish-confirm-action')
            focusField(action?.hasAttribute('disabled') ? 'publish-form-heading' : 'publish-confirm-action')
          })
        }}
        onReplacePackage={() => {
          setSubmissionState(null)
          requestAnimationFrame(() => focusField(`${previewId}-replace-action`))
        }}
        onViewMySkills={() => navigate({ to: '/dashboard/skills' })}
        onViewVersion={result => navigate({ to: '/space/$namespace/$slug', params: { namespace: result.namespace, slug: result.slug }, search: { version: result.version } })}
      />}
      <div hidden={!!submissionState} inert={!!submissionState} className="space-y-8">
        <div id="publish-form-heading" tabIndex={-1}>
          <DashboardPageHeader title={t(isUpdate ? 'publish.updateTitle' : 'publish.title')} subtitle={t(isUpdate ? 'publish.updateSubtitle' : 'publish.subtitle')} />
        </div>

        {attempted && !avatarOnly && missingFields.length > 0 && <div role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 p-4 text-destructive">
          <p>{t('publish.source.requiredSummary')}</p>
          <div className="mt-2 flex flex-wrap gap-x-4 gap-y-2">
            {missingFields.map(field => <button key={field.id} type="button" className="underline underline-offset-4" onClick={() => focusField(field.id)}>{t(field.label)}</button>)}
          </div>
        </div>}
        {publishedFailed && <div role="alert" className="flex flex-wrap items-center gap-3 rounded-lg border p-4">
          <p>{t('publish.source.historyUnavailable')}</p>
          <Button type="button" variant="outline" onClick={() => retryPublished()}>{t('publish.retryPackagePreview')}</Button>
        </div>}
        {ownerReadOnly && <p role="status" className="rounded-lg border bg-muted/20 p-3 text-sm text-muted-foreground">{t('mySkills.ownerReadOnly')}</p>}
        {prefill.resubmitSkill && prefill.resubmitVersion && <div className="rounded-xl border border-amber-500/25 bg-amber-500/5 p-4">
          <h2 className="text-sm font-semibold">{t('publish.resubmitNotice.title', { skill: `@${prefill.namespace}/${prefill.resubmitSkill}`, version: prefill.resubmitVersion })}</h2>
          <p className="mt-1 text-sm text-muted-foreground">{t('publish.resubmitNotice.description')}</p>
        </div>}

        <div className="grid min-w-0 gap-x-10 gap-y-10 lg:grid-cols-[minmax(0,1fr)_344px]">
          <section id="publish-package-section" tabIndex={-1} aria-labelledby="publish-package-label" aria-invalid={attempted && !selectedFile} className="min-w-0 space-y-3 scroll-mt-24 lg:col-start-1 lg:row-start-1">
            <h2 id="publish-package-label" className="text-base font-semibold">{t('publish.file')} <span className="text-destructive">*</span></h2>
            {isUpdate && <p className="text-sm text-muted-foreground">{t('publish.optionalPackage')}</p>}
            <UploadZone
              key={`${namespaceSlug}/${updateSlug}`}
              formatHint={t('publish.packageFormatHint')}
              analyticsId="skill.package.select"
              folderAnalyticsId="skill.package.folder.select"
              pickerAnalyticsId="skill.package.folder.open"
              selectedFile={selectedFile}
              previewStatus={selectedFile ? packagePreview.isPending ? 'reading' : packagePreview.isError ? 'error' : packagePreview.isSuccess ? 'ready' : 'idle' : 'idle'}
              previewError={packagePreview.isError ? t('publish.packagePreviewFailed') : undefined}
              isPackaging={isPackaging}
              onFileSelect={handleFileSelect}
              onFolderSelect={handleFolderSelect}
              onRemove={handleRemoveSelectedFile}
              replaceActionId={`${previewId}-replace-action`}
              disabled={ownerReadOnly || isBusy}
            />
            {folderError && <p role="alert" className="text-sm text-destructive">{folderError}</p>}
            {attempted && !selectedFile && <p role="alert" className="text-sm text-destructive">{t('publish.selectRequired')}</p>}
            {selectedFile && packagePreview.isError && <Button data-analytics-id="skill.package.preview.retry" type="button" size="sm" variant="outline" disabled={ownerReadOnly || isBusy} onClick={() => packagePreview.refetch()}>{t('publish.retryPackagePreview')}</Button>}
          </section>

          <section aria-labelledby="publish-identity-heading" className="min-w-0 space-y-5 lg:col-start-1 lg:row-start-2">
            <h2 id="publish-identity-heading" className="text-lg font-semibold">{t('publish.form.identity')}</h2>
            <div className="grid min-w-0 gap-6 sm:grid-cols-[minmax(0,240px)_minmax(0,1fr)]">
              <div className="min-w-0 space-y-3">
                <SkillAvatarEditor key={`${namespaceSlug}/${updateSlug}`} name={displayName || search.updateName || updateSlug} currentUrl={avatarTarget.data?.avatarUrl} canRemove={!!updateSlug} disabled={ownerReadOnly || isBusy || avatarUnavailable} onChange={setAvatarEdit} />
                {updateSlug && <SkillAvatarManager namespace={namespaceSlug} slug={updateSlug} disabled={ownerReadOnly || isBusy} />}
                {avatarTarget.isError && <p role="alert" className="text-sm text-destructive">{t('skillAvatar.failed')}</p>}
              </div>
              <div className="min-w-0 space-y-2">
                <Label htmlFor="skill-display-name">{t('publish.displayName')}</Label>
                <Input
                  id="skill-display-name"
                  value={resourceReady ? displayName : ''}
                  maxLength={200}
                  disabled={ownerReadOnly || isBusy || !selectedFile}
                  placeholder={isUpdate ? search.updateName : t('publish.displayNamePlaceholder')}
                  aria-describedby="skill-display-name-help"
                  onChange={event => { displayNameEdited.current = true; setDisplayName(event.target.value) }}
                />
                <p id="skill-display-name-help" className="text-xs leading-5 text-muted-foreground">{t('publish.form.nameHelp')}</p>
                {selectedFile && packagePreview.data && <p className="break-all text-xs text-muted-foreground"><code>{packagePreview.data.name}</code> · {packagePreview.data.version}</p>}
                {selectedFile && packagePreview.data?.examplesStatus === 'ready' && <p role="status" className="text-xs text-muted-foreground">{t('publish.examplesReady', { count: packagePreview.data.examplesCount })}</p>}
                {selectedFile && packagePreview.data?.examplesStatus === 'none' && <p className="text-xs text-muted-foreground">{t('publish.examplesNone')}</p>}
                {selectedFile && packagePreview.data?.examplesStatus === 'invalid' && <p role="status" className="text-xs text-muted-foreground">{t('publish.examplesInvalid')}</p>}
              </div>
            </div>
          </section>

          <aside aria-labelledby="publish-settings-heading" className="min-w-0 self-start rounded-xl bg-muted/40 p-5 sm:p-6 lg:sticky lg:top-24 lg:col-start-2 lg:row-start-1 lg:row-span-3">
            <h2 id="publish-settings-heading" className="text-lg font-semibold">{t('publish.settings')}</h2>
            <div className="mt-6 space-y-6">
              <div className="space-y-3">
                <Label htmlFor="namespace">{t('publish.namespace')} <span className="text-destructive">*</span></Label>
                {isLoadingNamespaces ? <div className="h-11 animate-shimmer rounded-lg" /> : <Select
                  disabled={!!updateSlug || ownerReadOnly || isBusy}
                  value={normalizeSelectValue(namespaceSlug) ?? EMPTY_NAMESPACE_VALUE}
                  onValueChange={value => { setNamespaceSlug(value === EMPTY_NAMESPACE_VALUE ? '' : value); setAvatarEdit(KEEP_AVATAR) }}
                >
                  <SelectTrigger data-analytics-id="skill.publish.namespace.interact" id="namespace" aria-invalid={attempted && !namespaceSlug}><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem data-analytics-id="skill.publish.namespace.clear" value={EMPTY_NAMESPACE_VALUE}>{t('publish.selectNamespace')}</SelectItem>
                    {namespaces?.map(ns => <SelectItem data-analytics-id="skill.publish.namespace.select" key={ns.slug} value={ns.slug}>{ns.displayName} (@{ns.slug})</SelectItem>)}
                  </SelectContent>
                </Select>}
              </div>
              <div className="space-y-3">
                <Label htmlFor="visibility">{t('publish.visibility')} <span className="text-destructive">*</span></Label>
                <Select value={visibility} onValueChange={setVisibility} disabled={ownerReadOnly || isBusy || avatarOnly}>
                  <SelectTrigger data-analytics-id="skill.publish.visibility.interact" id="visibility" aria-describedby="publish-visibility-help"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem data-analytics-id="skill.publish.visibility.public" value="PUBLIC">{t('publish.visibilityOptions.public')}</SelectItem>
                    <SelectItem data-analytics-id="skill.publish.visibility.namespace" value="NAMESPACE_ONLY">{namespaceOnlyLabel}</SelectItem>
                    <SelectItem data-analytics-id="skill.publish.visibility.private" value="PRIVATE">{t('publish.visibilityOptions.private')}</SelectItem>
                  </SelectContent>
                </Select>
                <p id="publish-visibility-help" className="text-xs leading-5 text-muted-foreground">{t('publish.reviewNotice.description')}</p>
              </div>
            </div>
          </aside>

          <section aria-labelledby="publish-presentation-heading" className="min-w-0 space-y-8 border-t pt-8 lg:col-start-1 lg:row-start-3">
            <div className="space-y-2">
              <h2 id="publish-presentation-heading" className="text-lg font-semibold">{t('publish.form.presentation')}</h2>
              <p className="text-sm leading-6 text-muted-foreground">{t('publish.form.presentationHelp')}</p>
            </div>
            <div className="space-y-3">
              <Label htmlFor="skill-short-description">{t('publish.shortDescription')} <span className="text-destructive">*</span></Label>
              <Textarea
                aria-label={t('publish.shortDescription')}
                aria-required
                id="skill-short-description"
                rows={4}
                className="min-h-[120px] resize-y"
                maxLength={200}
                value={previewShortDescription}
                disabled={ownerReadOnly || isBusy}
                aria-invalid={attempted && shortInvalid}
                aria-describedby={`skill-short-description-help${shortInvalid && selectedFile ? ' skill-short-description-error' : ''}`}
                onChange={event => {
                  if (event.target.value === previewShortDescription) return
                  sourceTouched.current.short = true
                  shortDescriptionEdited.current = true
                  setShortDescription(event.target.value)
                  setShortSource('custom')
                }}
              />
              <div id="skill-short-description-help" className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span>{t(shortSource === 'custom' ? 'publish.form.customized' : 'publish.form.packageContent')}</span>
                  {shortSource === 'custom' && <button type="button" className="inline-flex items-center gap-1 text-foreground underline-offset-4 hover:underline focus-visible:underline disabled:opacity-50" disabled={ownerReadOnly || isBusy || !selectedFile || !packagePreview.isSuccess} onClick={event => { restoreOpener.current = event.currentTarget; setRestoreField('short') }}><RotateCcw aria-hidden className="h-3 w-3" />{t('publish.form.restore')}</button>}
                </div>
                <span className="tabular-nums">{previewShortDescription.length} / 200</span>
              </div>
              {shortInvalid && selectedFile && <p id="skill-short-description-error" role={attempted ? 'alert' : 'status'} className={attempted ? 'text-sm text-destructive' : 'text-sm text-amber-700 dark:text-amber-400'}>{t('publish.source.shortRequired')}</p>}
            </div>

            <div className="space-y-3">
              <div className="flex items-center justify-between gap-3">
                <h3 id="skill-overview-label" tabIndex={-1} className="text-sm font-medium scroll-mt-24">{t('publish.overview')} <span className="text-destructive">*</span></h3>
                <Button type="button" variant="outline" size="sm" disabled={ownerReadOnly || isBusy || readingOverview || readingPublished || (!!selectedFile && packagePreview.isPending)} onClick={openOverviewEditor}><Pencil className="mr-2 h-4 w-4" aria-hidden />{t('publish.editOverview')}</Button>
              </div>
              <div className="rounded-xl border border-border bg-background p-5 sm:p-6" aria-labelledby="skill-overview-label" aria-describedby={overviewInvalid && selectedFile ? 'skill-overview-error' : undefined}>
                {readingOverview ? <p role="status" className="py-5 text-sm text-muted-foreground">{t('publish.readingOverview')}</p> : overviewPreviewFailed ? <p role="status" className="py-5 text-sm text-muted-foreground">{t('publish.overviewPreviewUnavailable')}</p> : previewOverview.trim() ? <div className="max-h-80 overflow-hidden [mask-image:linear-gradient(black_85%,transparent)]"><MarkdownRenderer content={previewOverview} /></div> : <div className="py-5"><h4 className="font-medium">{t('publish.emptyOverview')}</h4><p className="mt-2 text-sm text-muted-foreground">{t('publish.emptyOverviewHelp')}</p></div>}
              </div>
              <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
                <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                  <span>{t(overviewSource === 'custom' ? 'publish.form.customized' : 'publish.form.packageContent')}</span>
                  {overviewSource === 'custom' && <button type="button" className="inline-flex items-center gap-1 text-foreground underline-offset-4 hover:underline focus-visible:underline disabled:opacity-50" disabled={ownerReadOnly || isBusy || !selectedFile || !packagePreview.isSuccess || readingPackageOverview} onClick={event => { restoreOpener.current = event.currentTarget; setRestoreField('overview') }}><RotateCcw aria-hidden className="h-3 w-3" />{t('publish.form.restore')}</button>}
                </div>
                {previewOverview.trim() && <button type="button" className="underline-offset-4 hover:underline focus-visible:underline" disabled={isBusy} onClick={() => setViewOverview(true)}>{t('publish.source.view')}</button>}
              </div>
              {overviewInvalid && selectedFile && <p id="skill-overview-error" role={attempted ? 'alert' : 'status'} className={attempted ? 'text-sm text-destructive' : 'text-sm text-amber-700 dark:text-amber-400'}>{t('publish.source.overviewRequired')}</p>}
              {overviewPreviewFailed && <Button type="button" variant="outline" disabled={ownerReadOnly || isBusy} onClick={() => packageOverview.refetch()}>{t('publish.retryPackagePreview')}</Button>}
            </div>
          </section>
        </div>

        <footer className="flex flex-wrap items-center justify-between gap-4 border-t pt-6">
          <p className="text-xs leading-5 text-muted-foreground">{t('publish.form.confirmHelp')}</p>
          <Button id="publish-confirm-action" data-analytics-id={avatarOnly ? 'skill.avatar.save' : 'skill.publish.request'}
            className="w-full text-primary-foreground disabled:text-primary-foreground sm:w-auto sm:min-w-40"
            size="lg"
            onClick={() => {
              setAttempted(true)
              if (canSubmit) { if (avatarOnly) void handlePublish(); else setConfirmOpen(true) }
              else focusField(missingFields[0]?.id ?? 'publish-form-heading')
            }}
            disabled={ownerReadOnly || isBusy || isPackaging || !avatarEdit.ready || avatarUnavailable || (avatarOnly && !canSubmit) || readingOverview || readingPublished || (!!selectedFile && packagePreview.isPending)}
          >{isBusy ? t('publish.publishing') : t(avatarOnly ? 'publish.saveAvatar' : 'publish.nextConfirm')}</Button>
        </footer>

        <Dialog open={viewOverview} onOpenChange={setViewOverview}>
          <DialogContent className="w-[min(calc(100vw-2rem),56rem)]"><DialogTitle>{t('publish.overview')}</DialogTitle><MarkdownRenderer content={previewOverview} /></DialogContent>
        </Dialog>
        <PresentationEditor open={editorOpen} value={editorOriginal.current} maxLength={20000} onCancel={() => setEditorOpen(false)} onApply={value => {
          if (ownerReadOnly || isBusy) { setEditorOpen(false); return }
          if (value !== editorOriginal.current) {
            sourceTouched.current.overview = true
            overviewEdited.current = true
            setOverview(value)
            setOverviewSource('custom')
          }
          setEditorOpen(false)
        }} />
        <ConfirmDialog
          open={restoreField !== null}
          onOpenChange={open => { if (!open) setRestoreField(null) }}
          title={t('publish.form.restoreTitle', { field: t(restoreField === 'short' ? 'publish.shortDescription' : 'publish.overview') })}
          description={t('publish.form.restoreDescription')}
          confirmText={t('publish.form.restore')}
          cancelText={t('dialog.cancel')}
          onConfirm={restorePackageContent}
          onCloseAutoFocus={event => {
            event.preventDefault()
            const target = restoredFocus.current
            const opener = restoreOpener.current
            restoredFocus.current = null
            restoreOpener.current = null
            requestAnimationFrame(() => {
              if (target) focusField(target)
              else if (opener?.isConnected && !opener.disabled) opener.focus()
              else focusField('publish-form-heading')
            })
          }}
        />
        <ConfirmDialog open={confirmOpen} onOpenChange={setConfirmOpen} title={t('publish.confirmDetails')} description={<span className="whitespace-pre-line">{[displayName, `@${namespaceSlug}/${packagePreview.data?.name}@${packagePreview.data?.version}`, `${t('publish.visibility')}: ${visibility === 'PUBLIC' ? t('publish.visibilityOptions.public') : visibility === 'PRIVATE' ? t('publish.visibilityOptions.private') : namespaceOnlyLabel}`, previewShortDescription, t('publish.reviewNotice.description')].join('\n\n')}</span>} confirmText={t('publish.confirm')} cancelText={t('publish.backToEdit')} onConfirm={() => { if (!canSubmit) return; setConfirmOpen(false); void handlePublish() }} />
        <ConfirmDialog confirmAnalyticsId={avatarOnly ? undefined : 'skill.publish.confirm'} cancelAnalyticsId={avatarOnly ? undefined : 'skill.publish.cancel'}
          open={warningDialogOpen}
          onOpenChange={setWarningDialogOpen}
          title={t('publish.warningConfirmTitle')}
          description={<div className="space-y-3 text-left"><p>{t('publish.warningConfirmDescription')}</p>{precheckWarnings.length > 0 && <ul className="list-disc space-y-1 pl-5">{precheckWarnings.map(warning => <li key={warning}>{warning}</li>)}</ul>}</div>}
          confirmText={t('publish.warningConfirmContinue')}
          cancelText={t('publish.warningConfirmCancel')}
          onConfirm={() => publishSkill(true)}
        />
      </div>
    </div>
  )
}
