import { useEffect, useState } from 'react'
import { useBlocker } from '@tanstack/react-router'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { authoringApi } from '@/api/client'
import type { DraftFileSummary } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { toast } from '@/shared/lib/toast'
import { fileToBase64, formatBytes } from './binary-file'
import { QueryError } from './query-error'

export function DraftFileEditor({ draftId, files, revision, onSaved, onDirtyChange }: {
  draftId: number, files: DraftFileSummary[], revision: number,
  onSaved: () => void, onDirtyChange: (dirty: boolean) => void,
}) {
  const { t } = useTranslation()
  const client = useQueryClient()
  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [newPath, setNewPath] = useState('')
  const [uploadFile, setUploadFile] = useState<File | null>(null)
  const [busy, setBusy] = useState(false)
  const [dirty, setDirty] = useState(false)
  const [discardPath, setDiscardPath] = useState<string | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [reloadOpen, setReloadOpen] = useState(false)
  const [editorGeneration, setEditorGeneration] = useState(0)
  const paths = files.flatMap(file => file.path ? [file.path] : []).sort()
  const path = selectedPath && paths.includes(selectedPath) ? selectedPath : paths[0] ?? null
  const selected = files.find(file => file.path === path)
  const readKey = ['authoring', 'draft', draftId, 'content', path]
  const read = useQuery({
    queryKey: readKey,
    queryFn: async () => {
      if (!path) throw new Error(t('authoring.files.empty'))
      // Capture the revision at the start of this read; later refreshes must not
      // silently authorize an edit based on stale bytes against a newer revision.
      const before = await authoringApi.getDraft(draftId)
      if (before.revision == null) throw new Error(t('authoring.ui.loadFailed'))
      const file = await authoringApi.readFile(draftId, path)
      return { ...file, revision: before.revision }
    },
    enabled: path != null, meta: { skipGlobalErrorHandler: true }, retry: false,
    staleTime: Infinity,
  })
  const blocker = useBlocker({ shouldBlockFn: () => dirty || busy, enableBeforeUnload: dirty || busy, withResolver: true })
  useEffect(() => { onDirtyChange(dirty || busy) }, [dirty, busy, onDirtyChange])

  const choosePath = (next: string) => {
    if (next === path) return
    if (dirty) setDiscardPath(next)
    else setSelectedPath(next)
  }
  const mutateFile = async (operation: () => Promise<unknown>, successKey: string, nextPath?: string) => {
    if (busy) return
    setBusy(true)
    try {
      await operation()
      setDirty(false)
      if (nextPath) setSelectedPath(nextPath)
      await client.invalidateQueries({ queryKey: ['authoring', 'draft', draftId, 'content'] })
      onSaved()
      toast.success(t(successKey))
      return true
    } catch (error) {
      toast.error(error instanceof Error ? error.message : t('apiError.unknown'))
      return false
    } finally { setBusy(false) }
  }
  const create = async () => {
    const next = newPath.trim().replace(/\\/g, '/').replace(/(^|\/)skill\.md$/i, '$1SKILL.md')
    if (!next || busy || dirty) return
    // Creating a file must never overwrite an existing resource with empty bytes.
    if (paths.includes(next)) {
      toast.error(t('authoring.files.exists'))
      return
    }
    if (await mutateFile(() => authoringApi.saveFile(draftId, { path: next, content: '', expectedRevision: revision }), 'authoring.files.created', next)) setNewPath('')
  }
  const upload = async () => {
    if (!uploadFile || !newPath.trim() || busy || dirty) return
    const file = uploadFile
    const next = newPath.trim()
    const ok = await mutateFile(async () => authoringApi.saveFile(draftId, {
      path: next, content: await fileToBase64(file), base64: true,
      contentType: file.type || undefined, expectedRevision: revision,
    }), 'authoring.files.uploaded', next)
    if (ok) { setUploadFile(null); setNewPath('') }
  }
  return (
    <div className="grid min-w-0 gap-4 lg:grid-cols-[220px_minmax(0,1fr)]">
      <div className="min-w-0 space-y-3">
        <ul className="space-y-1" aria-label={t('authoring.files.title')}>
          {paths.map(name => <li key={name}><button type="button" disabled={busy} aria-pressed={path === name} title={name} onClick={() => choosePath(name)} className={`w-full break-all rounded-md px-2 py-2 text-left font-mono text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring ${path === name ? 'bg-primary text-primary-foreground' : 'hover:bg-muted'}`}>{name}</button></li>)}
        </ul>
        <Label htmlFor="new-file-path">{t('authoring.files.path')}</Label>
        <Input id="new-file-path" value={newPath} disabled={busy || dirty} onChange={event => setNewPath(event.target.value)} placeholder="scripts/run.py" />
        <Button size="sm" variant="outline" onClick={() => void create()} disabled={busy || dirty || !newPath.trim()}>{t('authoring.files.create')}</Button>
        <Label htmlFor="upload-file">{t('authoring.files.upload')}</Label>
        <Input id="upload-file" type="file" disabled={busy || dirty} onChange={event => {
          const file = event.target.files?.[0] ?? null
          setUploadFile(file)
          if (file && !newPath.trim()) setNewPath(`assets/${file.name}`)
        }} />
        <p className="text-xs text-muted-foreground">{t('authoring.files.replaceHint')}</p>
        <Button size="sm" variant="outline" onClick={() => void upload()} disabled={busy || dirty || !uploadFile || !newPath.trim()}>{t(busy ? 'authoring.files.uploading' : 'authoring.files.uploadConfirm')}</Button>
      </div>
      <div className="min-w-0 space-y-3">
        {!path && <p>{t('authoring.files.empty')}</p>}
        {path && read.isPending && <p role="status">{t('authoring.ui.loading')}</p>}
        {path && read.isError && <QueryError error={read.error} retry={() => void read.refetch()} />}
        {path && read.isSuccess && (read.data.encoding === 'text' ? <TextFileEditor key={`${draftId}:${path}:${read.data.sha256}:${editorGeneration}`} content={read.data.content ?? ''} disabled={busy} onDirtyChange={setDirty} onSave={content => mutateFile(() => authoringApi.saveFile(draftId, { path, content, expectedRevision: read.data.revision }), 'authoring.files.saved')} /> : <div className="space-y-2 rounded-md border p-4" data-testid="binary-file-panel">
          <p className="break-all font-mono text-sm">{path}</p>
          <p className="text-sm">{t('authoring.files.size')}: {formatBytes(selected?.size ?? 0)}</p>
          <p className="break-all font-mono text-xs">sha256: {selected?.sha256}</p>
          <p className="text-sm text-muted-foreground">{t('authoring.files.binaryHint')}</p>
        </div>)}
        {path && <Button variant="outline" size="sm" disabled={busy} onClick={() => dirty ? setReloadOpen(true) : void read.refetch()}>{t('authoring.files.reload')}</Button>}
        {path && <Button variant="outline" size="sm" disabled={busy} onClick={() => setDeleteOpen(true)}>{t('authoring.files.delete')}</Button>}
      </div>
      <ConfirmDialog open={discardPath != null} onOpenChange={open => { if (!open) setDiscardPath(null) }} title={t('authoring.files.discardTitle')} description={t('authoring.files.discardDescription')} onConfirm={() => { setDirty(false); setSelectedPath(discardPath); setDiscardPath(null) }} />
      <ConfirmDialog open={blocker.status === 'blocked'} onOpenChange={open => { if (!open) blocker.reset?.() }} title={t('authoring.files.discardTitle')} description={t('authoring.files.discardDescription')} onConfirm={() => { if (!busy) blocker.proceed?.() }} closeOnConfirm={false} />
      <ConfirmDialog open={reloadOpen} onOpenChange={setReloadOpen} title={t('authoring.files.discardTitle')} description={t('authoring.files.discardDescription')} onConfirm={async () => { const result = await read.refetch(); if (result.isSuccess) { setEditorGeneration(value => value + 1); setDirty(false); setReloadOpen(false) } }} />
      <ConfirmDialog open={deleteOpen} onOpenChange={setDeleteOpen} title={t('authoring.files.deleteTitle')} description={t('authoring.files.deleteDescription', { path })} variant="destructive" closeOnConfirm={false} onConfirm={async () => {
        if (!path) return
        if (await mutateFile(() => authoringApi.deleteFile(draftId, path, read.data?.revision ?? revision), 'authoring.files.deleted')) { setDeleteOpen(false); setSelectedPath(null) }
      }} />
    </div>
  )
}

function TextFileEditor({ content: initial, disabled, onDirtyChange, onSave }: {
  content: string, disabled: boolean, onDirtyChange: (dirty: boolean) => void,
  onSave: (content: string) => Promise<boolean | undefined>,
}) {
  const { t } = useTranslation()
  const [content, setContent] = useState(initial)
  const [saved, setSaved] = useState(initial)
  const dirty = content !== saved
  useEffect(() => { onDirtyChange(dirty); return () => onDirtyChange(false) }, [dirty, onDirtyChange])
  return <div className="space-y-2">
    <Label htmlFor="draft-file-content">{t('authoring.files.content')}</Label>
    <Textarea id="draft-file-content" value={content} disabled={disabled} onChange={event => setContent(event.target.value)} rows={18} spellCheck={false} className="font-mono text-sm" />
    <div className="flex flex-wrap items-center gap-2">
      <Button size="sm" disabled={disabled || !dirty} onClick={async () => { if (await onSave(content)) setSaved(content) }}>{t('authoring.files.save')}</Button>
      {dirty && <span role="status" className="text-sm text-muted-foreground">{t('authoring.files.unsaved')}</span>}
    </div>
  </div>
}
