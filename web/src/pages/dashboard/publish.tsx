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
import { Label } from '@/shared/ui/label'
import { Card } from '@/shared/ui/card'
import { SkillAvatarEditor, KEEP_AVATAR, type AvatarEdit } from '@/features/skill/skill-avatar-editor'
import { SkillAvatarManager } from '@/features/skill/skill-avatar-manager'
import { previewSkillPackage, usePublishSkill, useSkillDetail } from '@/shared/hooks/use-skill-queries'
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
  const displayNameEdited = useRef(false)
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [namespaceSlug, setNamespaceSlug] = useState<string>(prefill.namespace)
  const [visibility, setVisibility] = useState<string>(prefill.visibility)
  const [warningDialogOpen, setWarningDialogOpen] = useState(false)
  const [precheckWarnings, setPrecheckWarnings] = useState<string[]>([])
  const [isPackaging, setIsPackaging] = useState(false)

  const { data: namespaces, isLoading: isLoadingNamespaces } = useMyNamespaces()
  const publishMutation = usePublishSkill()
  const { data: updateTarget } = useSkillDetail(namespaceSlug, updateSlug ?? '', !!namespaceSlug && !!updateSlug)
  const ownerReadOnly = updateTarget?.ownerReadOnly === true
  const packagePreview = useQuery({
    queryKey: ['skill-package-preview', previewId, fileRevision],
    queryFn: () => previewSkillPackage(selectedFile!),
    enabled: selectedFile !== null,
    retry: false,
    gcTime: 0,
  })
  useEffect(() => {
    if (packagePreview.data && !displayNameEdited.current) {
      setDisplayName(packagePreview.data.displayName ?? packagePreview.data.name ?? '')
    }
  }, [packagePreview.data])
  const avatarTarget = useQuery({ queryKey: ['skill-avatar', namespaceSlug, updateSlug], queryFn: () => skillAvatarApi.status(namespaceSlug, updateSlug), enabled: !!namespaceSlug && !!updateSlug })
  const avatarUnavailable = !!updateSlug && (avatarTarget.isLoading || avatarTarget.isError)
  const isBusy = publishMutation.isPending || isSavingAvatar
  const avatarOnly = isUpdate && !selectedFile
  const canSubmit = !ownerReadOnly && (!selectedFile || packagePreview.isSuccess) && displayName.trim().length <= 200 && !!namespaceSlug && avatarEdit.ready && !avatarUnavailable && !isBusy && (selectedFile !== null || (isUpdate && avatarEdit.action !== 'KEEP'))
  const selectedNamespace = namespaces?.find((ns) => ns.slug === namespaceSlug)
  const namespaceOnlyLabel = selectedNamespace?.type === 'GLOBAL'
    ? t('publish.visibilityOptions.loggedInUsersOnly')
    : t('publish.visibilityOptions.namespaceOnly')

  useEffect(() => {
    setNamespaceSlug(prefill.namespace)
    setVisibility(prefill.visibility)
    setAvatarEdit(KEEP_AVATAR)
  }, [prefill.namespace, prefill.visibility, updateSlug])

  const handleRemoveSelectedFile = () => {
    setSelectedFile(null)
    setPrecheckWarnings([])
    setWarningDialogOpen(false)
  }

  const handleFileSelect = (file: File | null) => {
    setSelectedFile(file)
    setFileRevision(value => value + 1)
    setPrecheckWarnings([])
    setWarningDialogOpen(false)
  }

  const handleFolderSelect = async (files: File[]) => {
    setIsPackaging(true)
    try {
      const zip = await packageFolderAsZip(files)
      handleFileSelect(zip)
    } catch {
      toast.error(t('publish.folderPackagingFailed'))
    } finally {
      setIsPackaging(false)
    }
  }

  const publishSkill = async (confirmWarnings = false) => {
    if (submitting.current) return
    if (!canSubmit) {
      toast.error(t(ownerReadOnly ? 'mySkills.ownerReadOnly' : 'publish.selectRequired'))
      return
    }

    submitting.current = true
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
      })
      setPrecheckWarnings([])
      setWarningDialogOpen(false)
      const avatarNotice = avatarEdit.action === 'REPLACE' ? t('skillAvatar.submittedNotice') : avatarEdit.action === 'REMOVE' ? t('skillAvatar.removedNotice') : ''
      const skillLabel = `${result.namespace}/${result.slug}@${result.version}`
      if (result.status === 'PUBLISHED') {
        toast.success(
          t('publish.publishedTitle'),
          [t('publish.publishedDescription', { skill: skillLabel }), avatarNotice].filter(Boolean).join(' ')
        )
      } else {
        toast.success(
          t('publish.pendingReviewTitle'),
          [t('publish.pendingReviewDescription', { skill: skillLabel }), avatarNotice].filter(Boolean).join(' ')
        )
      }
      navigateAfterOverlays(() => {
        navigate({ to: '/dashboard/skills' })
      })
    } catch (error) {
      // A package rejection may also reflect changed membership. Re-read the
      // server state instead of inferring permissions from its localized message.
      if (updateSlug && !avatarOnly && error instanceof ApiError && error.status === 400) {
        await queryClient.invalidateQueries({ queryKey: getSkillDetailQueryKey(namespaceSlug, updateSlug ?? '') })
      }
      // Avatar-only updates call the API directly rather than a mutation.
      if (avatarOnly) await refreshSkillPermissionsAfterForbidden(queryClient, error)
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

  return (
    <div className="mx-auto max-w-2xl space-y-8 animate-fade-up">
      <DashboardPageHeader title={t(isUpdate ? 'publish.updateTitle' : 'publish.title')} subtitle={t(isUpdate ? 'publish.updateSubtitle' : 'publish.subtitle')} />

      {ownerReadOnly && <p role="status" className="rounded-lg border bg-muted/20 p-3 text-sm text-muted-foreground">{t('mySkills.ownerReadOnly')}</p>}
      <Card className="space-y-8 p-6 md:p-8">
        {prefill.resubmitSkill && prefill.resubmitVersion ? (
          <div className="rounded-xl border border-amber-500/25 bg-amber-500/5 p-4">
            <h2 className="text-sm font-semibold text-foreground">
              {t('publish.resubmitNotice.title', {
                skill: `@${prefill.namespace}/${prefill.resubmitSkill}`,
                version: prefill.resubmitVersion,
              })}
            </h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {t('publish.resubmitNotice.description')}
            </p>
          </div>
        ) : null}
        <div className="flex items-start gap-3 rounded-xl border border-blue-500/20 bg-blue-500/5 p-4">
          <svg className="mt-0.5 h-5 w-5 flex-shrink-0 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
          <div className="flex-1">
            <h3 className="mb-1 text-sm font-semibold text-foreground">{t('publish.reviewNotice.title')}</h3>
            <p className="text-sm text-muted-foreground">{t('publish.reviewNotice.description')}</p>
          </div>
        </div>
        <div className="space-y-3">
          <Label htmlFor="namespace" className="text-sm font-semibold font-heading">{t('publish.namespace')}</Label>
          {isLoadingNamespaces ? (
            <div className="h-11 animate-shimmer rounded-lg" />
          ) : (
            <Select
              disabled={!!updateSlug || isBusy}
              value={normalizeSelectValue(namespaceSlug) ?? EMPTY_NAMESPACE_VALUE}
              onValueChange={(value) => {
                setNamespaceSlug(value === EMPTY_NAMESPACE_VALUE ? '' : value)
                setAvatarEdit(KEEP_AVATAR)
              }}
            >
              <SelectTrigger data-analytics-id="skill.publish.namespace.interact" id="namespace">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem data-analytics-id="skill.publish.namespace.clear" value={EMPTY_NAMESPACE_VALUE}>{t('publish.selectNamespace')}</SelectItem>
                {namespaces?.map((ns) => (
                  <SelectItem data-analytics-id="skill.publish.namespace.select" key={ns.id} value={ns.slug}>
                    {ns.displayName} (@{ns.slug})
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          )}
        </div>

        <div className="space-y-3">
          <Label htmlFor="visibility" className="text-sm font-semibold font-heading">{t('publish.visibility')}</Label>
          <Select value={visibility} onValueChange={setVisibility} disabled={ownerReadOnly || isBusy || avatarOnly}>
            <SelectTrigger data-analytics-id="skill.publish.visibility.interact" id="visibility">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem data-analytics-id="skill.publish.visibility.public" value="PUBLIC">{t('publish.visibilityOptions.public')}</SelectItem>
              <SelectItem data-analytics-id="skill.publish.visibility.namespace" value="NAMESPACE_ONLY">{namespaceOnlyLabel}</SelectItem>
              <SelectItem data-analytics-id="skill.publish.visibility.private" value="PRIVATE">{t('publish.visibilityOptions.private')}</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="space-y-3">
          <Label className="text-sm font-semibold font-heading">{t('publish.file')}</Label>
          {isUpdate && <p className="text-sm text-muted-foreground">{t('publish.optionalPackage')}</p>}
          <UploadZone analyticsId="skill.package.select" folderAnalyticsId="skill.package.folder.select" pickerAnalyticsId="skill.package.folder.open"
            onFileSelect={handleFileSelect}
            onFolderSelect={handleFolderSelect}
            disabled={ownerReadOnly || isBusy || isPackaging}
          />
          {selectedFile && (
            <div className="flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-secondary/30 px-4 py-3">
              <div className="min-w-0 text-sm text-muted-foreground flex items-center gap-2">
                <svg className="w-4 h-4 text-emerald-500 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
                <span className="truncate">
                  {selectedFile.name} ({(selectedFile.size / 1024).toFixed(1)} KB)
                </span>
              </div>
              <Button data-analytics-id="skill.package.remove"
                type="button"
                variant="outline"
                size="sm"
                onClick={handleRemoveSelectedFile}
                disabled={ownerReadOnly || isBusy}
              >
                {t('publish.removeSelectedFile')}
              </Button>
            </div>
          )}
        </div>

        <div className="space-y-2">
          <Label htmlFor="skill-display-name">{t('publish.displayName')}</Label>
          <Input
            id="skill-display-name"
            value={displayName}
            maxLength={200}
            disabled={ownerReadOnly || isBusy || !selectedFile}
            placeholder={isUpdate ? search.updateName : t('publish.displayNamePlaceholder')}
            aria-describedby="skill-display-name-help"
            onChange={event => {
              displayNameEdited.current = true
              setDisplayName(event.target.value)
            }}
          />
          <p id="skill-display-name-help" className="text-sm text-muted-foreground">{t('publish.displayNameHelp')}</p>
          {selectedFile && packagePreview.isPending && <p role="status" className="text-sm text-muted-foreground">{t('publish.readingPackage')}</p>}
          {selectedFile && packagePreview.isError && <div role="alert" className="text-sm text-destructive">
            <p>{t('publish.packagePreviewFailed')}</p>
            <Button data-analytics-id="skill.package.preview.retry" type="button" size="sm" variant="outline" onClick={() => packagePreview.refetch()}>{t('publish.retryPackagePreview')}</Button>
          </div>}
          {selectedFile && packagePreview.data && <p className="break-all text-xs text-muted-foreground">{t('publish.packageName')}: <code>{packagePreview.data.name}</code></p>}
          {selectedFile && packagePreview.data?.examplesStatus === 'ready' && <p role="status" className="text-xs text-muted-foreground">{t('publish.examplesReady', { count: packagePreview.data.examplesCount })}</p>}
          {selectedFile && packagePreview.data?.examplesStatus === 'none' && <p className="text-xs text-muted-foreground">{t('publish.examplesNone')}</p>}
          {selectedFile && packagePreview.data?.examplesStatus === 'invalid' && <p role="status" className="text-xs text-muted-foreground">{t('publish.examplesInvalid')}</p>}
        </div>

        <SkillAvatarEditor key={`${namespaceSlug}/${updateSlug}`} name={search.updateName ?? updateSlug} currentUrl={avatarTarget.data?.avatarUrl} canRemove={!!updateSlug} disabled={ownerReadOnly || isBusy || avatarUnavailable} onChange={setAvatarEdit} />
        {updateSlug && <SkillAvatarManager namespace={namespaceSlug} slug={updateSlug} disabled={ownerReadOnly || isBusy} />}
        {avatarTarget.isError && <p role="alert" className="text-sm text-destructive">{t('skillAvatar.failed')}</p>}

        <Button data-analytics-id={avatarOnly ? "skill.avatar.save" : "skill.publish.request"}
          className="w-full text-primary-foreground disabled:text-primary-foreground"
          size="lg"
          onClick={handlePublish}
          disabled={!canSubmit}
        >
          {isBusy ? t('publish.publishing') : t(avatarOnly ? 'publish.saveAvatar' : 'publish.confirm')}
        </Button>
      </Card>

      <ConfirmDialog confirmAnalyticsId={avatarOnly ? undefined : "skill.publish.confirm"} cancelAnalyticsId={avatarOnly ? undefined : "skill.publish.cancel"}
        open={warningDialogOpen}
        onOpenChange={setWarningDialogOpen}
        title={t('publish.warningConfirmTitle')}
        description={(
          <div className="space-y-3 text-left">
            <p>{t('publish.warningConfirmDescription')}</p>
            {precheckWarnings.length > 0 && (
              <ul className="list-disc space-y-1 pl-5">
                {precheckWarnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            )}
          </div>
        )}
        confirmText={t('publish.warningConfirmContinue')}
        cancelText={t('publish.warningConfirmCancel')}
        onConfirm={() => publishSkill(true)}
      />
    </div>
  )
}
