import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { FilePlus2, Save, Trash2, Upload } from 'lucide-react'
import { authoringApi } from '@/api/client'
import type { DraftFileSummary } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Textarea } from '@/shared/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { toast } from '@/shared/lib/toast'
import { fileToBase64, formatBytes, isBinaryDraftPath } from './binary-file'

/**
 * Draft file editor: a file picker plus a plain-text editor for text formats
 * and an upload-to-replace view for binary assets (images, documents). Saves
 * go through the draft's optimistic-concurrency revision so concurrent edits
 * surface as errors instead of silently overwriting each other.
 */
export function DraftFileEditor({
  draftId,
  files,
  revision,
  onSaved,
  onDeleted,
}: {
  draftId: number
  files: DraftFileSummary[]
  revision: number
  onSaved: (path: string) => void
  onDeleted: (path: string) => void
}) {
  const { t } = useTranslation()
  const sortedFiles = useMemo(
    () => [...files].sort((a, b) => a.path.localeCompare(b.path)),
    [files],
  )
  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [content, setContent] = useState('')
  const [dirty, setDirty] = useState(false)
  const [saving, setSaving] = useState(false)
  const [newPath, setNewPath] = useState('')
  const [creating, setCreating] = useState(false)

  const [uploadOpen, setUploadOpen] = useState(false)
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [uploadPath, setUploadPath] = useState('')
  const [uploading, setUploading] = useState(false)

  const selected = sortedFiles.find((file) => file.path === selectedPath) ?? null
  const binarySelected = selectedPath != null && isBinaryDraftPath(selectedPath)

  useEffect(() => {
    if (sortedFiles.length === 0) {
      setSelectedPath(null)
      return
    }
    if (selectedPath == null) {
      setSelectedPath(sortedFiles[0].path)
    }
  }, [sortedFiles, selectedPath])

  useEffect(() => {
    if (selectedPath == null || isBinaryDraftPath(selectedPath)) {
      return
    }
    let cancelled = false
    authoringApi
      .readFile(draftId, selectedPath)
      .then((file) => {
        if (!cancelled) {
          setContent(file.content)
          setDirty(false)
        }
      })
      .catch((error) => {
        if (!cancelled) {
          toast.error(error instanceof Error ? error.message : String(error))
        }
      })
    return () => {
      cancelled = true
    }
  }, [draftId, selectedPath])

  const save = async () => {
    if (selectedPath == null) {
      return
    }
    setSaving(true)
    try {
      await authoringApi.saveFile(draftId, {
        path: selectedPath,
        content,
        expectedRevision: revision,
      })
      setDirty(false)
      onSaved(selectedPath)
      toast.success(t('authoring.files.saved'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : String(error))
    } finally {
      setSaving(false)
    }
  }

  const createFile = async () => {
    const path = newPath.trim()
    if (!path) {
      return
    }
    setCreating(true)
    try {
      const outcome = await authoringApi.saveFile(draftId, {
        path,
        content: '',
        expectedRevision: revision,
      })
      setNewPath('')
      onSaved(path)
      setSelectedPath(path)
      toast.success(
        outcome.created ? t('authoring.files.created') : t('authoring.files.exists'),
      )
    } catch (error) {
      toast.error(error instanceof Error ? error.message : String(error))
    } finally {
      setCreating(false)
    }
  }

  const remove = async () => {
    if (selectedPath == null) {
      return
    }
    const removedPath = selectedPath
    try {
      await authoringApi.deleteFile(draftId, removedPath, revision)
      // pick the next selection from the files we know remain — the list prop
      // is still stale at this point, so the removed path itself must be
      // excluded explicitly
      const next = sortedFiles.find((file) => file.path !== removedPath)?.path ?? null
      onDeleted(removedPath)
      setSelectedPath(next)
      toast.success(t('authoring.files.deleted'))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : String(error))
    }
  }

  const openUpload = (prefillPath?: string) => {
    setUploadFile(null)
    setUploadPath(prefillPath ?? '')
    setUploadOpen(true)
  }

  const chooseUploadFile = (file: File | null) => {
    setUploadFile(file)
    if (file && !uploadPath.trim()) {
      setUploadPath(`assets/${file.name}`)
    }
  }

  const upload = async () => {
    if (!uploadFile) {
      return
    }
    const path = uploadPath.trim()
    if (!path) {
      return
    }
    setUploading(true)
    try {
      const base64 = await fileToBase64(uploadFile)
      const outcome = await authoringApi.saveFile(draftId, {
        path,
        content: base64,
        base64: true,
        contentType: uploadFile.type || undefined,
        expectedRevision: revision,
      })
      setUploadOpen(false)
      onSaved(path)
      setSelectedPath(path)
      toast.success(
        outcome.created ? t('authoring.files.uploaded') : t('authoring.files.replaced'),
      )
    } catch (error) {
      toast.error(error instanceof Error ? error.message : String(error))
    } finally {
      setUploading(false)
    }
  }

  return (
    <div className="grid gap-4 lg:grid-cols-[220px_1fr]">
      <div className="space-y-2">
        <ul className="space-y-1" data-testid="draft-file-list">
          {sortedFiles.map((file) => (
            <li key={file.path}>
              <button
                type="button"
                onClick={() => setSelectedPath(file.path)}
                className={`w-full truncate rounded-md px-2 py-1.5 text-left font-mono text-xs ${
                  file.path === selectedPath
                    ? 'bg-primary text-primary-foreground'
                    : 'hover:bg-muted'
                }`}
              >
                {file.path}
              </button>
            </li>
          ))}
        </ul>
        <div className="flex gap-1">
          <Input
            value={newPath}
            onChange={(event) => setNewPath(event.target.value)}
            placeholder="scripts/run.py"
            className="h-8 font-mono text-xs"
            onKeyDown={(event) => {
              if (event.key === 'Enter') {
                void createFile()
              }
            }}
          />
          <Button size="icon" variant="outline" className="h-8 w-8 shrink-0" onClick={() => void createFile()} disabled={creating || !newPath.trim()} aria-label={t('authoring.files.create')}>
            <FilePlus2 className="h-4 w-4" aria-hidden />
          </Button>
        </div>
        <Button
          size="sm"
          variant="outline"
          className="w-full"
          onClick={() => openUpload()}
          data-testid="upload-file-button"
        >
          <Upload className="mr-1 h-4 w-4" aria-hidden />
          {t('authoring.files.upload')}
        </Button>
      </div>

      <div className="space-y-2">
        {selectedPath == null ? (
          <p className="text-sm text-muted-foreground">{t('authoring.files.empty')}</p>
        ) : binarySelected && selected ? (
          <div className="space-y-3 rounded-md border p-4" data-testid="binary-file-panel">
            <p className="font-mono text-sm">{selected.path}</p>
            <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-muted-foreground">
              <dt>{t('authoring.files.size')}</dt>
              <dd className="font-mono">{formatBytes(selected.size)}</dd>
              <dt>sha256</dt>
              <dd className="truncate font-mono" title={selected.sha256}>
                {selected.sha256.slice(0, 16)}…
              </dd>
              <dt>{t('authoring.files.contentType')}</dt>
              <dd className="font-mono">{selected.contentType || '—'}</dd>
            </dl>
            <p className="text-sm text-muted-foreground">{t('authoring.files.binaryHint')}</p>
            <div className="flex items-center gap-2">
              <Button size="sm" onClick={() => openUpload(selectedPath)} data-testid="replace-file">
                <Upload className="mr-1 h-4 w-4" aria-hidden />
                {t('authoring.files.replace')}
              </Button>
              <Button size="sm" variant="outline" onClick={() => void remove()} data-testid="delete-file">
                <Trash2 className="mr-1 h-4 w-4" aria-hidden />
                {t('authoring.files.delete')}
              </Button>
            </div>
          </div>
        ) : (
          <>
            <Textarea
              value={content}
              onChange={(event) => {
                setContent(event.target.value)
                setDirty(true)
              }}
              rows={20}
              className="font-mono text-xs"
              spellCheck={false}
              data-testid="file-editor"
            />
            <div className="flex items-center gap-2">
              <Button size="sm" onClick={() => void save()} disabled={saving || !dirty} data-testid="save-file">
                <Save className="mr-1 h-4 w-4" aria-hidden />
                {t('authoring.files.save')}
              </Button>
              <Button size="sm" variant="outline" onClick={() => void remove()} data-testid="delete-file">
                <Trash2 className="mr-1 h-4 w-4" aria-hidden />
                {t('authoring.files.delete')}
              </Button>
              {dirty && <span className="text-xs text-amber-600">{t('authoring.files.unsaved')}</span>}
            </div>
          </>
        )}
      </div>

      <Dialog open={uploadOpen} onOpenChange={setUploadOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('authoring.files.uploadTitle')}</DialogTitle>
            <DialogDescription>{t('authoring.files.uploadDescription')}</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <Input
              type="file"
              onChange={(event) => chooseUploadFile(event.target.files?.[0] ?? null)}
              data-testid="upload-file-input"
            />
            <div className="space-y-1">
              <label className="text-xs text-muted-foreground" htmlFor="upload-path">
                {t('authoring.files.path')}
              </label>
              <Input
                id="upload-path"
                value={uploadPath}
                onChange={(event) => setUploadPath(event.target.value)}
                placeholder="assets/logo.png"
                className="font-mono text-xs"
                data-testid="upload-path-input"
              />
            </div>
            {uploadFile && (
              <p className="text-xs text-muted-foreground">
                {uploadFile.name} · {formatBytes(uploadFile.size)}
              </p>
            )}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setUploadOpen(false)}>
              {t('authoring.ui.cancel')}
            </Button>
            <Button
              onClick={() => void upload()}
              disabled={uploading || !uploadFile || !uploadPath.trim()}
              data-testid="confirm-upload"
            >
              {uploading ? t('authoring.files.uploading') : t('authoring.files.uploadConfirm')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
