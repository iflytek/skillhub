import { useEffect, useRef, useState } from 'react'
import { AlertTriangle, CheckCircle2, FileArchive, RefreshCw, ShieldAlert, XCircle } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from '@tanstack/react-router'
import type { SkillSuiteBundlePreview } from '@/api/types'
import { packageFolderAsZip } from '@/features/publish/folder-zip'
import { UploadZone } from '@/features/publish/upload-zone'
import {
  useCancelSuiteBundleOperation,
  useConfirmSuiteBundle,
  usePreviewSuiteBundle,
  useRetrySuiteBundleOperation,
  useSuiteBundleOperation,
} from '@/shared/hooks/use-suite-queries'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { toast } from '@/shared/lib/toast'
import { validateSuiteBundleFolder, validateSuiteBundleZip } from './suite-bundle-folder'

type BundleMode = 'CREATE' | 'UPDATE'

function operationStorageKey(mode: BundleMode, coordinate?: string): string {
  return `skillhub:suite-bundle-operation:${mode}:${coordinate ?? 'new'}`
}

function readStoredOperation(key: string): string | undefined {
  try {
    return window.sessionStorage.getItem(key) || undefined
  } catch {
    return undefined
  }
}

function writeStoredOperation(key: string, operationId?: string): void {
  try {
    if (operationId) window.sessionStorage.setItem(key, operationId)
    else window.sessionStorage.removeItem(key)
  } catch {
    // Browsers can disable session storage. The active page still remains usable.
  }
}

function splitCoordinate(coordinate?: string): { namespace: string; slug: string } | null {
  const match = coordinate?.match(/^@([^/]+)\/(.+)$/)
  return match ? { namespace: match[1], slug: match[2] } : null
}

export function SuiteBundleImport({ expectedMode, expectedCoordinate }: {
  expectedMode: BundleMode
  expectedCoordinate?: string
}) {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const storageKey = operationStorageKey(expectedMode, expectedCoordinate)
  const previewMutation = usePreviewSuiteBundle()
  const confirmMutation = useConfirmSuiteBundle()
  const cancelMutation = useCancelSuiteBundleOperation()
  const retryMutation = useRetrySuiteBundleOperation()
  const [preview, setPreview] = useState<SkillSuiteBundlePreview | null>(null)
  const [fileName, setFileName] = useState('')
  const [warningsAccepted, setWarningsAccepted] = useState(false)
  const [operationId, setOperationId] = useState<string | undefined>(() => readStoredOperation(storageKey))
  const [packaging, setPackaging] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const requestRef = useRef<AbortController | null>(null)
  const selectionVersionRef = useRef(0)
  const idempotencyKeyRef = useRef<string | null>(null)
  const operationQuery = useSuiteBundleOperation(operationId)

  useEffect(() => () => {
    selectionVersionRef.current += 1
    requestRef.current?.abort()
  }, [])
  useEffect(() => writeStoredOperation(storageKey, operationId), [operationId, storageKey])
  useEffect(() => {
    if (!preview) return undefined
    const timer = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(timer)
  }, [preview])

  const beginSelection = () => {
    const selectionVersion = ++selectionVersionRef.current
    requestRef.current?.abort()
    requestRef.current = null
    setPackaging(false)
    setPreview(null)
    setOperationId(undefined)
    setWarningsAccepted(false)
    idempotencyKeyRef.current = null
    return selectionVersion
  }

  const uploadArchive = async (file: File, selectionVersion: number) => {
    if (selectionVersion !== selectionVersionRef.current) return
    const validationError = validateSuiteBundleZip(file)
    if (validationError) {
      toast.error(t(`suite.bundle.errors.${validationError}`))
      return
    }
    const controller = new AbortController()
    requestRef.current = controller
    setFileName(file.name)
    try {
      const result = await previewMutation.mutateAsync({ file, signal: controller.signal })
      if (!controller.signal.aborted && selectionVersion === selectionVersionRef.current) {
        idempotencyKeyRef.current = crypto.randomUUID()
        setNow(Date.now())
        setPreview(result)
      }
    } catch (error) {
      if (!controller.signal.aborted && selectionVersion === selectionVersionRef.current) {
        toast.error(t('suite.bundle.previewFailed'), error instanceof Error ? error.message : '')
      }
    } finally {
      if (requestRef.current === controller) requestRef.current = null
    }
  }

  const previewFile = async (file: File) => {
    const selectionVersion = beginSelection()
    await uploadArchive(file, selectionVersion)
  }

  const previewFolder = async (files: File[]) => {
    const selectionVersion = beginSelection()
    const validationError = validateSuiteBundleFolder(files)
    if (validationError) {
      toast.error(t(`suite.bundle.errors.${validationError}`))
      return
    }
    setPackaging(true)
    try {
      const archive = await packageFolderAsZip(files)
      if (selectionVersion === selectionVersionRef.current) await uploadArchive(archive, selectionVersion)
    } catch (error) {
      if (selectionVersion === selectionVersionRef.current) {
        toast.error(t('suite.bundle.packageFailed'), error instanceof Error ? error.message : '')
      }
    } finally {
      if (selectionVersion === selectionVersionRef.current) setPackaging(false)
    }
  }

  const targetMatches = preview?.target?.mode === expectedMode
    && (expectedCoordinate === undefined || preview.target.coordinate === expectedCoordinate)
  const targetMismatch = preview !== null && !targetMatches
  const previewExpired = Boolean(preview?.expiresAt && Date.parse(preview.expiresAt) <= now)
  const warningCount = (preview?.warnings?.length ?? 0)
    + (preview?.members ?? []).reduce((count, member) => count + (member.warnings?.length ?? 0), 0)
  const removalCount = preview?.removedMembers?.length ?? 0
  const requiresAcknowledgement = warningCount > 0 || removalCount > 0
  const canConfirm = Boolean(
    preview?.confirmable
    && preview.previewToken
    && preview.warningDigest
    && targetMatches
    && !previewExpired
    && (!requiresAcknowledgement || warningsAccepted)
  )

  const confirm = async () => {
    if (!preview?.previewToken || !preview.warningDigest || !canConfirm) return
    const idempotencyKey = idempotencyKeyRef.current ?? crypto.randomUUID()
    idempotencyKeyRef.current = idempotencyKey
    try {
      const result = await confirmMutation.mutateAsync({
        previewToken: preview.previewToken,
        warningDigest: preview.warningDigest,
        idempotencyKey,
      })
      if (result.operationId) setOperationId(result.operationId)
    } catch (error) {
      toast.error(t('suite.bundle.confirmFailed'), error instanceof Error ? error.message : '')
    }
  }

  if (operationId) {
    const operation = operationQuery.data
    const status = operation?.status
    const draftCoordinate = splitCoordinate(operation?.targetCoordinate)
    const resetOperation = () => {
      setOperationId(undefined)
      setPreview(null)
      setFileName('')
    }
    return (
      <Card className="space-y-5 p-6" aria-live="polite">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="font-semibold">{t('suite.bundle.progressTitle')}</h2>
            <p className="mt-1 text-sm text-muted-foreground">
              {operationQuery.isLoading
                ? t('suite.bundle.loadingOperation')
                : status
                  ? t(`suite.bundle.status.${status}`)
                  : t('suite.bundle.operationLoadFailed')}
            </p>
          </div>
          <span className="rounded-full bg-secondary px-3 py-1 font-mono text-xs">{status ?? 'RUNNING'}</span>
        </div>
        <p className="break-all font-mono text-xs text-muted-foreground">{operationId}</p>
        {operationQuery.error ? (
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
            <span>{t('suite.bundle.operationLoadFailed')}</span>
            <Button variant="outline" size="sm" onClick={() => operationQuery.refetch()}>
              <RefreshCw className="mr-2 h-4 w-4" />{t('suite.bundle.reloadOperation')}
            </Button>
          </div>
        ) : null}
        {operation?.failureCode ? (
          <div className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
            {operation.failureCode}
          </div>
        ) : null}
        <div className="space-y-2">
          {(operation?.members ?? []).map((member) => (
            <div key={member.position} className="space-y-2 rounded-lg border p-3 text-sm">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <span className="min-w-0 break-all font-mono">
                  {member.redacted ? t('suite.bundle.redactedMember') : member.coordinate}
                </span>
                <span className="font-mono text-xs text-muted-foreground">{member.status}</span>
              </div>
              {!member.redacted ? (
                <>
                  <p className="text-xs text-muted-foreground">
                    {member.sourceType ? t(`suite.bundle.source.${member.sourceType}`) : null}
                    {member.relationship ? ` · ${t(`suite.bundle.relationship.${member.relationship}`)}` : null}
                    {member.publishAction ? ` · ${t(`suite.bundle.action.${member.publishAction}`)}` : null}
                  </p>
                  {member.packagePath ? (
                    <p className="break-all text-xs text-muted-foreground">
                      {t('suite.bundle.memberDirectory', { path: member.packagePath })}
                    </p>
                  ) : null}
                  <p className="text-xs text-muted-foreground">
                    {member.visibility ?? '—'} · v{member.version ?? '—'}
                  </p>
                  {(member.errors ?? []).map((error) => <p key={error} className="text-xs text-destructive">{error}</p>)}
                  {(member.warnings ?? []).map((warning) => <p key={warning} className="text-xs text-amber-700 dark:text-amber-300">{warning}</p>)}
                  {member.coordinate && member.version && member.status === 'WAITING_FOR_MEMBER' ? (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => {
                        const coordinate = splitCoordinate(member.coordinate)
                        if (coordinate) {
                          navigate({
                            to: `/space/${coordinate.namespace}/${encodeURIComponent(coordinate.slug)}`,
                            search: { version: member.version },
                          })
                        }
                      }}
                    >
                      {t('suite.bundle.viewMemberReview')}
                    </Button>
                  ) : null}
                </>
              ) : null}
            </div>
          ))}
        </div>
        <div className="flex flex-wrap justify-end gap-3">
          {status === 'BLOCKED_RETRYABLE' ? (
            <Button
              variant="outline"
              disabled={retryMutation.isPending}
              onClick={() => retryMutation.mutate(operationId, {
                onError: (error) => toast.error(t('suite.bundle.retryFailed'), error.message),
              })}
            >
              <RefreshCw className="mr-2 h-4 w-4" />{t('suite.bundle.retry')}
            </Button>
          ) : null}
          {status !== 'CANCELLED' && status !== 'REPREVIEW_REQUIRED' && status !== 'SUITE_DRAFT_CREATED' ? (
            <Button
              variant="outline"
              disabled={cancelMutation.isPending}
              onClick={() => cancelMutation.mutate(operationId, {
                onError: (error) => toast.error(t('suite.bundle.cancelFailed'), error.message),
              })}
            >
              {t('suite.bundle.cancelOperation')}
            </Button>
          ) : null}
          {status === 'REPREVIEW_REQUIRED' || status === 'CANCELLED' ? (
            <Button onClick={resetOperation}>
              {t('suite.bundle.chooseAgain')}
            </Button>
          ) : null}
          {operationQuery.error ? (
            <Button variant="ghost" onClick={resetOperation}>{t('suite.bundle.forgetOperation')}</Button>
          ) : null}
          {status === 'SUITE_DRAFT_CREATED' && draftCoordinate && operation?.targetVersion ? (
            <>
              <Button variant="outline" onClick={resetOperation}>{t('suite.bundle.importAnother')}</Button>
              <Button onClick={() => navigate({
                to: `/suite/${draftCoordinate.namespace}/${encodeURIComponent(draftCoordinate.slug)}`,
                search: { version: operation.targetVersion },
              })}>
                {t('suite.bundle.openDraft')}
              </Button>
            </>
          ) : null}
        </div>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      <Card className="space-y-4 p-6">
        <div className="flex items-start gap-3">
          <FileArchive className="mt-0.5 h-5 w-5 text-primary" aria-hidden="true" />
          <div>
            <h2 className="font-semibold">{t('suite.bundle.uploadTitle')}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{t('suite.bundle.uploadDescription')}</p>
          </div>
        </div>
        <UploadZone
          onFileSelect={previewFile}
          onFolderSelect={previewFolder}
          disabled={previewMutation.isPending || packaging}
        />
        {fileName ? <p className="break-all text-xs text-muted-foreground">{fileName}</p> : null}
        {previewMutation.isPending || packaging ? (
          <div className="h-20 animate-shimmer rounded-lg" aria-label={t('suite.bundle.processing')} />
        ) : null}
      </Card>

      {preview ? (
        <Card className="space-y-5 p-6">
          <div className="flex items-start justify-between gap-4">
            <div>
              <h2 className="font-semibold">{t('suite.bundle.previewTitle')}</h2>
              <p className="mt-1 text-sm text-muted-foreground">
                {preview.target?.coordinate} · v{preview.target?.targetVersion}
              </p>
            </div>
            {preview.confirmable && !targetMismatch
              ? <CheckCircle2 className="h-5 w-5 text-success" aria-label={t('suite.bundle.confirmable')} />
              : <XCircle className="h-5 w-5 text-destructive" aria-label={t('suite.bundle.notConfirmable')} />}
          </div>

          {targetMismatch ? (
            <div className="flex gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              <ShieldAlert className="h-4 w-4 shrink-0" />{t('suite.bundle.targetMismatch')}
            </div>
          ) : null}
          {previewExpired ? (
            <div className="flex gap-2 rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">
              <AlertTriangle className="h-4 w-4 shrink-0" />{t('suite.bundle.previewExpired')}
            </div>
          ) : null}

          {preview.target ? (
            <div className="rounded-lg border bg-muted/20 p-4">
              <h3 className="font-medium">{preview.target.displayName}</h3>
              <p className="mt-1 text-sm text-muted-foreground">{preview.target.summary}</p>
              <p className="mt-3 whitespace-pre-wrap text-sm text-foreground">{preview.target.overview}</p>
            </div>
          ) : null}

          {(preview.errors ?? []).map((error) => (
            <p key={error} className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{error}</p>
          ))}
          {(preview.warnings ?? []).map((warning) => (
            <p key={warning} className="rounded-lg border border-amber-500/30 bg-amber-500/5 p-3 text-sm text-amber-700 dark:text-amber-300">{warning}</p>
          ))}

          <div className="space-y-2">
            {(preview.members ?? []).map((member) => (
              <div key={member.coordinate} className="rounded-lg border p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <span className="font-mono text-sm font-medium">{member.coordinate}</span>
                  <span className="rounded-full bg-secondary px-2 py-1 text-xs">
                    {t(`suite.bundle.relationship.${member.relationship}`)} · {t(`suite.bundle.action.${member.publishAction}`)}
                  </span>
                </div>
                <p className="mt-2 text-xs text-muted-foreground">
                  {member.sourceType ? t(`suite.bundle.source.${member.sourceType}`) : null}
                  {' · '}{member.finalVisibility} · v{member.resolvedVersion}
                </p>
                {member.packagePath ? (
                  <p className="mt-1 break-all text-xs text-muted-foreground">
                    {t('suite.bundle.memberDirectory', { path: member.packagePath })}
                  </p>
                ) : null}
                <p className="mt-1 text-xs text-muted-foreground">
                  {member.sourceType === 'REFERENCE' || member.publishAction === 'REUSE_VERSION'
                    ? t('suite.bundle.path.noWrite')
                    : member.finalVisibility === 'PRIVATE'
                      ? t('suite.bundle.path.private')
                      : t('suite.bundle.path.publish')}
                </p>
                {(member.errors ?? []).map((error) => <p key={error} className="mt-2 text-xs text-destructive">{error}</p>)}
                {(member.warnings ?? []).map((warning) => <p key={warning} className="mt-2 text-xs text-amber-700 dark:text-amber-300">{warning}</p>)}
              </div>
            ))}
            {(preview.removedMembers ?? []).map((member) => (
              <div key={member.coordinate} className="flex gap-2 rounded-lg border border-destructive/40 bg-destructive/5 p-4 text-sm text-destructive">
                <AlertTriangle className="h-4 w-4 shrink-0" />
                <span>
                  {t('suite.bundle.removedMember', { coordinate: member.coordinate, version: member.version })}
                  {member.entry ? ` · ${t('suite.bundle.removedEntryMember')}` : null}
                </span>
              </div>
            ))}
            {(preview.members ?? []).every((member) => member.relationship === 'UNCHANGED')
              && (preview.removedMembers?.length ?? 0) === 0 ? (
                <p className="rounded-lg border p-4 text-center text-sm text-muted-foreground">
                  {t('suite.bundle.noChanges')}
                </p>
              ) : null}
          </div>

          {requiresAcknowledgement ? (
            <label className="flex items-start gap-2 rounded-lg border p-3 text-sm">
              <input
                type="checkbox"
                checked={warningsAccepted}
                onChange={(event) => setWarningsAccepted(event.target.checked)}
              />
              <span>{t('suite.bundle.acceptRisks', { warningCount, removalCount })}</span>
            </label>
          ) : null}

          <div className="flex justify-end gap-3">
            <Button variant="outline" onClick={() => { setPreview(null); setFileName('') }}>
              {t('suite.bundle.chooseAgain')}
            </Button>
            <Button disabled={!canConfirm || confirmMutation.isPending} onClick={confirm}>
              {t('suite.bundle.confirm')}
            </Button>
          </div>
        </Card>
      ) : null}
    </div>
  )
}
