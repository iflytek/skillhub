import { useLayoutEffect, useRef, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { DragDropProvider } from '@dnd-kit/react'
import { isSortable, useSortable } from '@dnd-kit/react/sortable'
import { ArrowLeft, GripVertical, Pencil, Plus, Trash2, Upload, Check, X, ArrowUpRight } from 'lucide-react'
import { recommendationApi, recommendationSkills, type RecommendationContent, type RecommendationTopic } from '@/api/recommendations'
import { sitePresentationApi } from '@/api/site-presentation'
import type { RecommendedSkill } from '@/api/recommendations'
import { SkillAvatar } from '@/features/skill/skill-avatar'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/shared/ui/dialog'
import { labelApi } from '@/api/client'
import { Switch } from '@/shared/ui/switch'
import { RecommendationCover, RECOMMENDATION_COVER_STYLES, recommendationCoverStyle } from './recommendation-cover'
import './recommendations.css'
import './editor.css'

type Mode = 'topics' | 'featured'
export function moveRecommendation<T>(items: T[], from: number, to: number): T[] {
  if (from < 0 || to < 0 || from >= items.length || to >= items.length) return items
  const next = [...items]
  const [item] = next.splice(from, 1)
  next.splice(to, 0, item)
  return next
}
function SortableRow({ id, index, label, children, dragging }: { id: string; index: number; label: string; children: ReactNode; dragging: (active: boolean) => void }) {
  const { ref, handleRef, isDragging } = useSortable({ id, index })
  return <tr ref={ref} data-dragging={isDragging}>
    <td className="!px-2"><Button ref={handleRef} type="button" size="xs" variant="ghost" className="recommendation-handle" aria-label={label} onKeyDown={event => { if (event.key === ' ' ) dragging(true) }}><GripVertical aria-hidden className="h-3.5 w-3.5" /></Button></td>
    {children}
  </tr>
}

export function RecommendationEditor({ mode, initial, initialSkills, initialEditingId, onApply, onClose }: {
  initialEditingId?: string; mode: Mode; initial: RecommendationContent; initialSkills: RecommendedSkill[];
  onApply: (value: RecommendationContent, skills: RecommendedSkill[]) => void; onClose: () => void
}) {
  const { t, i18n } = useTranslation()
  const [working, setWorking] = useState(() => structuredClone(initial))
  const [known, setKnown] = useState(initialSkills)
  const [editing, setEditing] = useState<string | null>(initialEditingId ?? null)
  const [picking, setPicking] = useState(false)
  const [label, setLabel] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)
  const [error, setError] = useState('')
  const [uploading, setUploading] = useState(false)
  const [announcement, setAnnouncement] = useState('')
  const dragActive = useRef(false)
  const canceledDragAt = useRef(0)
  const editorSnapshot = useRef<RecommendationContent | null>(initialEditingId ? structuredClone(initial) : null)
  const pickerSnapshot = useRef<number[] | null>(null)
  const editorTrigger = useRef<HTMLElement | null>(null)
  const pickerTrigger = useRef<HTMLElement | null>(null)
  const restoreFocus = useRef<HTMLElement | null>(null)
  const searchInput = useRef<HTMLInputElement>(null)
  const fileInput = useRef<HTMLInputElement>(null)
  const workspace = useRef<HTMLDivElement>(null)
  useLayoutEffect(() => {
    workspace.current?.scrollTo?.({ top: 0 })
    const target = restoreFocus.current
    restoreFocus.current = null
    if (target?.isConnected) target.focus()
    else if (target) {
      const label = target.getAttribute('aria-label')
      const replacement = [...(workspace.current?.querySelectorAll<HTMLButtonElement>('button') ?? [])].find(button => label ? button.getAttribute('aria-label') === label : button.textContent === target.textContent)
      replacement?.focus()
    } else if (picking) searchInput.current?.focus()
    else if (editing) titleInput.current?.focus()
  }, [editing, picking])
  const titleInput = useRef<HTMLInputElement>(null)
  const results = useQuery({ queryKey: ['system-config', 'recommendation-candidates', q, page, label, i18n.resolvedLanguage ?? i18n.language], queryFn: () => recommendationApi.candidates(q, page, label), enabled: picking, retry: false, meta: { skipGlobalErrorHandler: true } })
  const labels = useQuery({ queryKey: ['labels', 'visible'], queryFn: () => labelApi.listVisible(), enabled: picking, retry: false, meta: { skipGlobalErrorHandler: true } })
  const topic = working.topics.find(item => item.id === editing)
  const selected = topic ? topic.skillIds : working.featuredSkillIds
  const skillsById = new Map(known.map(skill => [skill.id, skill]))
  const title = picking ? t('recommendations.chooseSkills') : editing ? t('recommendations.editTopic') : t(`recommendations.manage${mode === 'topics' ? 'Topics' : 'Featured'}`)
  function updateTopic(patch: Partial<RecommendationTopic>) {
    setWorking(value => ({ ...value, topics: value.topics.map(item => item.id === editing ? { ...item, ...patch } : item) }))
    setError('')
  }
  function updateSelected(ids: number[]) {
    if (topic) updateTopic({ skillIds: ids })
    else setWorking(value => ({ ...value, featuredSkillIds: ids }))
  }
  function remember(skills: RecommendedSkill[]) { setKnown(current => [...new Map([...current, ...skills].map(skill => [skill.id, skill])).values()]) }
  function validateTopic(): boolean {
    if (!topic) return true
    if (!topic.title.trim() || topic.enabled && !topic.skillIds.length) {
      setError(t('recommendations.topicRequired')); titleInput.current?.focus(); return false
    }
    return true
  }
  function apply() {
    if (!validateTopic()) return
    if (working.topics.some(item => !item.title.trim() || item.enabled && !item.skillIds.length)) {
      setError(t('recommendations.topicRequired')); return
    }
    onApply(working, known)
  }
  function beginEditor(id: string, next = working) {
    editorTrigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    editorSnapshot.current = structuredClone(working)
    setWorking(next); setEditing(id); setError('')
  }
  function beginPicker() {
    pickerTrigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    pickerSnapshot.current = [...selected]
    setPicking(true); setQ(''); setLabel(''); setPage(0); setError('')
  }
  function leaveLayer(commit: boolean) {
    if (uploading) return
    if (picking) {
      if (!commit && pickerSnapshot.current) updateSelected(pickerSnapshot.current)
      pickerSnapshot.current = null
      restoreFocus.current = pickerTrigger.current
      setPicking(false)
    } else if (editing) {
      if (commit && !validateTopic()) return
      if (!commit && editorSnapshot.current) setWorking(editorSnapshot.current)
      editorSnapshot.current = null
      restoreFocus.current = editorTrigger.current
      setEditing(null)
    } else { onClose() }
    setError('')
  }
  async function upload(file: File | undefined) {
    if (!file || uploading) return
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 2 * 1024 * 1024) { setError(t('recommendations.coverUploadHelp')); return }
    setUploading(true); setError('')
    try { const result = await sitePresentationApi.upload(file); updateTopic({ coverUrl: result.url }) }
    catch (cause) { setError(cause instanceof Error ? cause.message : t('sitePresentation.uploadFailed')) }
    finally { setUploading(false) }
  }
  function list(ids: number[], kind: 'topics' | 'skills') {
    return <DragDropProvider onDragStart={() => { dragActive.current = true }} onDragEnd={event => {
      dragActive.current = false
      if (event.canceled) { canceledDragAt.current = Date.now(); return }
      const { source } = event.operation
      if (!isSortable(source) || source.initialIndex === source.index) return
      const from = source.initialIndex, to = source.index
      if (kind === 'topics') setWorking(value => ({ ...value, topics: moveRecommendation(value.topics, from, to) }))
      else updateSelected(moveRecommendation(ids, from, to))
      setAnnouncement(t('recommendations.moved', { position: to + 1 }))
    }}>
      <div className="recommendation-editor-table" data-kind={kind}><table className="recommendation-table"><colgroup><col style={{ width: 40 }} /><col />{kind === 'topics' && <><col style={{ width: 148 }} /><col style={{ width: 112 }} /></>}<col style={{ width: kind === 'topics' ? 76 : 64 }} /></colgroup>
        <thead><tr><th aria-label={t('recommendations.order')} /><th>{t(kind === 'topics' ? 'recommendations.topic' : 'recommendations.skill')}</th>{kind === 'topics' && <><th>{t('recommendations.skills')}</th><th>{t('recommendations.display')}</th></>}<th className="!text-right">{t('recommendations.actions')}</th></tr></thead>
        <tbody>{ids.map((id, index) => {
          const item = working.topics[index]
          const skill = skillsById.get(id)
          const label = kind === 'topics' ? item.title : skill?.displayName ?? t('recommendations.unavailableSkill', { id })
          return <SortableRow key={kind === 'topics' ? item.id : id} id={kind === 'topics' ? item.id : String(id)} index={index} label={t('recommendations.drag', { name: label })} dragging={active => { dragActive.current = active }}>
            <td>{kind === 'topics' ? <div className="recommendation-topic-cell"><div className="recommendation-mini-cover"><RecommendationCover topicId={item.id} title={item.title} skillNames={item.skillIds.flatMap(id => skillsById.get(id)?.displayName ?? [])} coverStyle={item.coverStyle} coverUrl={item.coverUrl} /></div><div className="min-w-0"><strong className="block text-sm font-semibold">{label}</strong><p className="mt-1 line-clamp-2 text-xs text-muted-foreground">{item.description}</p></div></div> : <div className="flex min-h-8 min-w-0 items-center gap-3">{skill && <SkillAvatar name={skill.displayName} url={skill.avatarUrl} seed={`${skill.namespace}/${skill.slug}`} size="h-8 w-8 text-sm" />}<div className="min-w-0"><span className="block break-words text-sm font-medium">{label}</span>{skill && <span className="block break-all text-xs text-muted-foreground">@{skill.namespace}/{skill.slug}</span>}</div></div>}</td>
            {kind === 'topics' && <><td><div className="recommendation-avatar-stack">{item.skillIds.slice(0, 3).map(skillId => { const preview = skillsById.get(skillId); return preview ? <SkillAvatar key={skillId} name={preview.displayName} url={preview.avatarUrl} seed={`${preview.namespace}/${preview.slug}`} size="h-7 w-7 text-xs" /> : null })}<span className="ml-2 text-xs text-muted-foreground">{item.skillIds.length}</span></div></td><td><div className="flex items-center gap-2"><Switch aria-label={t('recommendations.displayTopic', { name: item.title })} checked={item.enabled} onCheckedChange={enabled => setWorking(value => ({ ...value, topics: value.topics.map(row => row.id === item.id ? { ...row, enabled } : row) }))} /><span className="text-xs text-muted-foreground">{t(item.enabled ? 'recommendations.visible' : 'recommendations.hidden')}</span></div></td></>}
            <td><div className="flex justify-end gap-1">{kind === 'topics' && <Button size="xs" variant="ghost" className="w-6 !px-0" aria-label={t('recommendations.editNamed', { name: label })} onClick={() => beginEditor(item.id)}><Pencil aria-hidden className="h-3.5 w-3.5" /></Button>}<Button size="xs" variant="ghost" className="w-6 !px-0" aria-label={t('recommendations.removeNamed', { name: label })} onClick={() => kind === 'topics' ? setWorking(value => ({ ...value, topics: value.topics.filter(row => row.id !== item.id) })) : updateSelected(ids.filter(value => value !== id))}><Trash2 aria-hidden className="h-3.5 w-3.5" /></Button></div></td>
          </SortableRow>
        })}{!ids.length && <tr><td colSpan={kind === 'topics' ? 5 : 3} className="!py-8 text-center text-muted-foreground">{t('recommendations.noSelection')}</td></tr>}</tbody>
      </table></div>
    </DragDropProvider>
  }
  return <Dialog open onOpenChange={open => { if (!open) leaveLayer(false) }}><DialogContent hideClose className="recommendation-workspace grid-rows-[auto_minmax(0,1fr)_auto]" onEscapeKeyDown={event => {
    event.preventDefault()
    if (dragActive.current || Date.now() - canceledDragAt.current < 100) { dragActive.current = false; return }
    leaveLayer(false)
  }} onInteractOutside={event => event.preventDefault()}>

    <header className="recommendation-workspace-head"><div className="space-y-2">{(editing || picking) && <Button size="sm" variant="ghost" onClick={() => leaveLayer(false)} disabled={uploading}><ArrowLeft className="h-4 w-4" />{t('recommendations.back')}</Button>}<DialogTitle>{title}</DialogTitle><DialogDescription>{picking ? t('recommendations.onlyPublic') : t('recommendations.transactionHelp')}</DialogDescription></div><Button variant="ghost" size="icon" aria-label={t('recommendations.close')} disabled={uploading} onClick={() => leaveLayer(false)}><X aria-hidden className="h-4 w-4" /></Button></header>
    <div ref={workspace} className="recommendation-workspace-body">
      {picking ? <div className="recommendation-picker-layout"><div className="recommendation-picker-main">
        <Input ref={searchInput} aria-label={t('recommendations.search')} placeholder={t('recommendations.search')} value={q} onChange={event => { setQ(event.target.value); setPage(0) }} maxLength={100} />
        {labels.data && <div className="recommendation-label-filters" role="group" aria-label={t('recommendations.filterLabels')}><Button size="sm" variant={label ? 'ghost' : 'secondary'} aria-pressed={!label} onClick={() => { setLabel(''); setPage(0) }}>{t('recommendations.allLabels')}</Button>{labels.data.filter(item => item.type === 'RECOMMENDED').map(item => <Button key={item.slug} size="sm" variant={label === item.slug ? 'secondary' : 'ghost'} aria-pressed={label === item.slug} onClick={() => { setLabel(item.slug); setPage(0) }}>{item.displayName}</Button>)}</div>}
        {labels.isError && <div className="flex items-center gap-2 text-xs text-muted-foreground"><span>{t('recommendations.labelLoadFailed')}</span><Button size="sm" variant="ghost" onClick={() => void labels.refetch()}>{t('recommendations.retry')}</Button></div>}
        {results.isPending && <p role="status">{t('common.loading')}</p>}
        {results.isError && <div role="alert" className="space-y-2 text-sm text-destructive"><p>{t('recommendations.loadFailed')}</p><Button variant="outline" size="sm" onClick={() => void results.refetch()}>{t('recommendations.retry')}</Button></div>}
        <ul className="recommendation-candidate-list">{recommendationSkills(results.data?.items).map(skill => <li key={skill.id}><label className="recommendation-candidate"><input type="checkbox" className="recommendation-pick-input sr-only" aria-label={skill.displayName} checked={selected.includes(skill.id)} disabled={!selected.includes(skill.id) && selected.length >= 24} onChange={event => { remember([skill]); updateSelected(event.target.checked ? [...selected, skill.id] : selected.filter(id => id !== skill.id)) }} /><span className="recommendation-pick-box"><Check aria-hidden className="h-3 w-3" /></span><SkillAvatar name={skill.displayName} url={skill.avatarUrl} seed={`${skill.namespace}/${skill.slug}`} /><span className="min-w-0"><span className="block break-words text-sm font-medium">{skill.displayName}</span><span className="block break-all text-xs text-muted-foreground">@{skill.namespace}/{skill.slug}</span><span className="mt-1 line-clamp-2 block text-xs leading-5 text-muted-foreground">{skill.summary}</span></span></label></li>)}</ul>
        {results.data && !results.data.items?.length && <p className="p-6 text-center text-sm text-muted-foreground">{t('recommendations.noResults')}</p>}
        <div className="mt-4 flex items-center justify-between"><Button variant="outline" size="sm" disabled={page === 0 || results.isFetching} onClick={() => setPage(page - 1)}>{t('recommendations.previous')}</Button><span className="text-xs text-muted-foreground">{page + 1}</span><Button variant="outline" size="sm" disabled={(page + 1) * 20 >= (results.data?.total ?? 0) || results.isFetching} onClick={() => setPage(page + 1)}>{t('recommendations.next')}</Button></div>
      </div><aside className="recommendation-picker-selected"><h3 className="text-sm font-semibold">{t('recommendations.selectedSkills')} <span className="ml-2 font-normal text-muted-foreground">{selected.length} / 24</span></h3><p className="my-3 text-xs leading-5 text-muted-foreground" role="status">{t(selected.length >= 24 ? 'recommendations.selectionLimit' : 'recommendations.selectionHelp')}</p><div className="recommendation-selected-items">{selected.map(id => { const skill = skillsById.get(id); const name = skill?.displayName ?? t('recommendations.unavailableSkill', { id }); return <div key={id} className="recommendation-selected-item">{skill && <SkillAvatar name={skill.displayName} url={skill.avatarUrl} seed={`${skill.namespace}/${skill.slug}`} size="h-8 w-8 text-sm" />}<span className="min-w-0 flex-1 break-words text-xs font-medium">{name}</span><Button size="xs" variant="ghost" aria-label={t('recommendations.removeNamed', { name })} onClick={() => updateSelected(selected.filter(value => value !== id))}><X aria-hidden className="h-3.5 w-3.5" /></Button></div> })}{!selected.length && <p className="py-6 text-center text-xs text-muted-foreground">{t('recommendations.noSelection')}</p>}</div></aside></div> : topic ? <div className="recommendation-edit-layout"><div className="recommendation-edit-form">
        <div className="space-y-1.5"><Label htmlFor="recommendation-topic-title">{t('recommendations.topicTitle')} *</Label><Input ref={titleInput} id="recommendation-topic-title" value={topic.title} required maxLength={80} aria-invalid={!!error && !topic.title.trim()} aria-describedby={error ? 'recommendation-editor-error' : undefined} onChange={event => updateTopic({ title: event.target.value })} /><p className="recommendation-counter">{topic.title.length} / 80</p></div>
        <div className="space-y-1.5"><Label htmlFor="recommendation-topic-description">{t('recommendations.description')}</Label><Textarea id="recommendation-topic-description" value={topic.description} maxLength={300} onChange={event => updateTopic({ description: event.target.value })} /><p className="recommendation-counter">{topic.description.length} / 300</p></div>
        <div className="space-y-2"><Label>{t('recommendations.cover')}</Label><div className="recommendation-cover-options" role="group" aria-label={t('recommendations.coverStyle')}>{RECOMMENDATION_COVER_STYLES.map(style => <button type="button" key={style} disabled={uploading} aria-label={t(`recommendations.coverStyle${style}`)} aria-pressed={recommendationCoverStyle(topic.id, topic.coverStyle) === style} onClick={() => updateTopic({ coverStyle: style })}><RecommendationCover topicId={topic.id} title={topic.title} skillNames={topic.skillIds.flatMap(id => skillsById.get(id)?.displayName ?? [])} coverStyle={style} />{recommendationCoverStyle(topic.id, topic.coverStyle) === style && <Check aria-hidden className="recommendation-swatch-check" />}</button>)}</div>
        <p className="text-xs leading-5 text-muted-foreground">{t('recommendations.coverBackgroundHelp')}</p><div className="recommendation-cover-upload" aria-busy={uploading} onDragOver={event => event.preventDefault()} onDrop={event => { event.preventDefault(); void upload(event.dataTransfer.files[0]) }}><Upload aria-hidden className="h-5 w-5 shrink-0 text-muted-foreground" /><div className="min-w-0 flex-1"><p className="text-sm font-medium">{t('recommendations.customCover')}</p><p className="mt-1 text-xs text-muted-foreground">{uploading ? t('sitePresentation.uploading') : t('recommendations.coverUploadHelp')}</p></div><input ref={fileInput} id="recommendation-cover" type="file" accept="image/png,image/jpeg" disabled={uploading} className="sr-only" aria-label={t('recommendations.cover')} onChange={event => { void upload(event.target.files?.[0]); event.target.value = '' }} /><Button size="sm" variant="outline" disabled={uploading} onClick={() => fileInput.current?.click()}>{t('recommendations.selectImage')}</Button></div>{topic.coverUrl && <Button variant="ghost" size="sm" disabled={uploading} onClick={() => updateTopic({ coverUrl: undefined })}>{t('recommendations.removeCover')}</Button>}</div>
        <div><div className="mb-3 flex items-center justify-between"><h3 className="text-sm font-semibold">{t('recommendations.skills')} <span className="ml-1 font-normal text-muted-foreground">{topic.skillIds.length} / 24</span></h3><Button size="sm" variant="outline" onClick={beginPicker} disabled={uploading}><Plus aria-hidden className="h-4 w-4" />{t('recommendations.chooseSkills')}</Button></div>{list(topic.skillIds, 'skills')}<p className="mt-2 text-xs leading-5 text-muted-foreground">{t('recommendations.dragHelp')}</p></div>
      </div><aside className="recommendation-edit-preview"><p className="mb-3 text-xs font-medium text-muted-foreground">{t('recommendations.cardPreview')}</p><article aria-label={t('recommendations.cardPreview')} className="recommendation-live-card"><RecommendationCover topicId={topic.id} title={topic.title} skillNames={topic.skillIds.flatMap(id => skillsById.get(id)?.displayName ?? [])} coverStyle={topic.coverStyle} coverUrl={topic.coverUrl} /><div className="p-5"><h3 className="break-words text-lg font-semibold">{topic.title || t('recommendations.untitledTopic')}</h3><p className="mt-2 whitespace-pre-wrap break-words text-sm leading-6 text-muted-foreground">{topic.description}</p><div className="mt-5 flex items-center justify-between text-xs text-muted-foreground"><span>{t('recommendations.selectedCount', { count: topic.skillIds.length, max: 24 })}</span><span className="flex items-center gap-2">{t('recommendations.browseTopic')}<ArrowUpRight aria-hidden className="h-3.5 w-3.5" /></span></div></div></article><p className="mt-3 text-xs leading-5 text-muted-foreground">{t('recommendations.livePreviewHelp')}</p><div className="mt-5 flex items-center justify-between gap-4 border-t pt-4"><div><Label htmlFor="recommendation-topic-enabled">{t('recommendations.display')}</Label><p className="mt-1 text-xs leading-5 text-muted-foreground">{t('recommendations.draftDisplayHelp')}</p></div><Switch id="recommendation-topic-enabled" checked={topic.enabled} disabled={uploading} onCheckedChange={enabled => updateTopic({ enabled })} /></div></aside>
      </div> : <>
        <div className="flex flex-wrap items-center justify-between gap-2"><div className="space-y-1"><p className="text-xs text-muted-foreground">{t('recommendations.dragHelp')}</p>{mode === 'topics' && <p className="text-xs text-muted-foreground">{t('recommendations.draftDisplayHelp')}</p>}</div><Button size="sm" variant="outline" disabled={mode === 'topics' && working.topics.length >= 12} onClick={() => {
          if (mode === 'featured') { beginPicker(); return }
          const id = crypto.randomUUID(); beginEditor(id, { ...working, topics: [...working.topics, { id, title: '', description: '', coverUrl: undefined, enabled: true, skillIds: [] }] })
        }}><Plus className="h-4 w-4" />{t(mode === 'topics' ? 'recommendations.addTopic' : 'recommendations.chooseSkills')}</Button></div>
        {mode === 'topics' ? list(working.topics.map((_, index) => index), 'topics') : list(working.featuredSkillIds, 'skills')}
      </>}
      {error && <p id="recommendation-editor-error" role="alert" className="text-sm text-destructive">{error}</p>}
      <p role="status" className="sr-only">{announcement}</p>
    </div>
    <footer className="recommendation-workspace-foot"><p className="mr-auto text-xs text-muted-foreground">{t('recommendations.transactionHelp')}</p><Button variant="outline" disabled={uploading} onClick={() => leaveLayer(false)}>{t('recommendations.cancel')}</Button>{picking || editing ? <Button disabled={uploading} onClick={() => leaveLayer(true)}>{t('recommendations.done')}</Button> : <Button disabled={uploading} onClick={apply}>{t('recommendations.apply')}</Button>}</footer>
  </DialogContent></Dialog>
}
