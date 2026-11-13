import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useBlocker } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Eye } from 'lucide-react'
import { ApiError } from '@/api/client'
import { recommendationApi, recommendationSkills, RECOMMENDATION_KEY, type RecommendationAdmin, type RecommendationContent, type RecommendationDisplay } from '@/api/recommendations'
import type { RecommendedSkill } from '@/api/recommendations'
import { useAuth } from '@/features/auth/use-auth'
import { Button } from '@/shared/ui/button'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { RecommendationEditor } from './recommendation-editor'
import { RecommendationPreview } from './recommendation-preview'

export function recommendationDraftPreview(content: RecommendationContent, skills: RecommendedSkill[]): RecommendationDisplay {
  const byId = new Map(skills.map(skill => [skill.id, skill]))
  return {
    topics: content.topics.filter(topic => topic.enabled).map(topic => ({ ...topic,
      skillCount: topic.skillIds.filter(id => byId.has(id)).length,
      skills: topic.skillIds.flatMap(id => byId.get(id) ? [byId.get(id)!] : []),
    })).filter(topic => topic.skillCount > 0),
    featuredSkills: content.featuredSkillIds.flatMap(id => byId.get(id) ? [byId.get(id)!] : []),
  }
}

interface RecommendationSettingsPanelProps {
  onDirtyChange?: (dirty: boolean) => void
  onBusyChange?: (busy: boolean) => void
}

export function RecommendationSettingsPanel(props: RecommendationSettingsPanelProps) {
  const { user } = useAuth()
  return user ? <SessionRecommendationSettings key={user.userId} userId={user.userId} {...props} /> : null
}

function SessionRecommendationSettings({ userId, onDirtyChange, onBusyChange }: RecommendationSettingsPanelProps & { userId: string }) {
  const { t, i18n } = useTranslation()
  const client = useQueryClient()
  const queryKey = ['system-config', 'recommendations', userId, i18n.resolvedLanguage ?? i18n.language] as const
  const query = useQuery({ queryKey, queryFn: recommendationApi.admin, enabled: true, refetchOnWindowFocus: false, retry: false, meta: { skipGlobalErrorHandler: true } })
  const [draft, setDraft] = useState<{ value: RecommendationContent; version: number } | null>(null)
  const [known, setKnown] = useState<RecommendedSkill[] | null>(null)
  const [knownLocale, setKnownLocale] = useState<string>()
  const [mode, setMode] = useState<'topics' | 'featured' | null>(null)
  const [editId, setEditId] = useState<string>()
  const [preview, setPreview] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const [busy, setBusy] = useState(false)
  const [conflict, setConflict] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const pending = useRef(false)
  const editorVersion = useRef(0)
  const confirmVersion = useRef<number | null>(null)
  const editorTrigger = useRef<HTMLElement | null>(null)
  const publishTrigger = useRef<HTMLElement | null>(null)
  function closeEditor() {
    setMode(null)
    requestAnimationFrame(() => editorTrigger.current?.focus())
  }
  function openEditor(kind: 'topics' | 'featured', id?: string) {
    editorTrigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    editorVersion.current = draft?.version ?? query.data?.version ?? 0
    setEditId(id); setMode(kind)
  }
  const value = draft?.value ?? query.data?.draft
  const dirty = !!draft && JSON.stringify(draft.value) !== JSON.stringify(query.data?.draft)
  useEffect(() => { onDirtyChange?.(dirty || !!mode) }, [dirty, mode, onDirtyChange])
  useEffect(() => { onBusyChange?.(busy) }, [busy, onBusyChange])
  const blocker = useBlocker({ shouldBlockFn: () => dirty || busy || !!mode, enableBeforeUnload: dirty || busy || !!mode, withResolver: true })
  const serverSkills = recommendationSkills(query.data?.skills)
  const translated = new Map(serverSkills.map(skill => [skill.id, skill]))
  const skills = known?.map(skill => knownLocale === (i18n.resolvedLanguage ?? i18n.language)
    ? skill : { ...skill, labels: translated.get(skill.id)?.labels ?? [] }) ?? serverSkills
  function accept(response: RecommendationAdmin) {
    client.setQueryData(queryKey, response); setDraft(null); setKnown(null); setConflict(false)
  }
  function fail(cause: unknown) {
    setConflict(cause instanceof ApiError && cause.status === 409)
    setError(cause instanceof Error ? cause.message : t('recommendations.saveFailed'))
  }
  async function save(next: 'save' | 'preview' | 'publish') {
    if (!value || !query.data || pending.current) return
    if (next === 'publish') publishTrigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    pending.current = true; setBusy(true); setError(''); setMessage('')
    try {
      let savedVersion = draft?.version ?? query.data.version ?? 0
      if (dirty || next === 'publish') {
        const response = await recommendationApi.save(savedVersion, value)
        savedVersion = response.version ?? savedVersion
        accept(response)
      }
      if (next === 'preview') { setPreview(true) }
      else if (next === 'publish') { confirmVersion.current = savedVersion; setConfirm(true) }
      else setMessage(t('recommendations.draftSaved'))
    } catch (cause) { fail(cause) }
    finally { pending.current = false; setBusy(false) }
  }
  async function publish() {
    if (confirmVersion.current === null || pending.current) return
    pending.current = true; setBusy(true); setError('')
    try {
      const response = await recommendationApi.publish(confirmVersion.current)
      accept(response); setConfirm(false); setMessage(t('recommendations.published'))
      await client.invalidateQueries({ queryKey: RECOMMENDATION_KEY })
    } catch (cause) { fail(cause) }
    finally { pending.current = false; setBusy(false) }
  }
  const content = value ? recommendationDraftPreview(value, skills) : null
  return <section className="space-y-6">
    <header className="flex flex-wrap items-start justify-between gap-4"><div className="space-y-1"><h2 className="text-2xl font-semibold tracking-tight">{t('recommendations.settingsTitle')}</h2><p className="max-w-2xl text-sm leading-5 text-muted-foreground">{t('recommendations.settingsHelp')}</p></div><div className="flex flex-wrap gap-2">{preview ? <Button variant="outline" onClick={() => { setPreview(false) }}><ArrowLeft className="h-4 w-4" />{t('recommendations.backToSettings')}</Button> : <><Button variant="outline" disabled={!dirty || busy || conflict} onClick={() => void save('save')}>{busy ? t('systemConfig.saving') : t('recommendations.saveDraft')}</Button><Button variant="outline" disabled={!value || busy || conflict} onClick={() => void save('preview')}><Eye className="h-4 w-4" />{t('recommendations.preview')}</Button><Button disabled={!value || busy || conflict} onClick={() => void save('publish')}>{t('recommendations.publish')}</Button></>}</div></header>
    {query.isPending && <p role="status">{t('common.loading')}</p>}
    {query.isError && <div role="alert"><p>{t('recommendations.loadFailed')}</p><Button variant="outline" onClick={() => void query.refetch()}>{t('recommendations.retry')}</Button></div>}
    {error && <p role="alert" className="rounded-lg border border-destructive/30 bg-destructive/5 p-3 text-sm text-destructive">{error}</p>}
    {conflict && <div className="flex flex-wrap items-center gap-3 text-sm"><p>{t('recommendations.conflict')}</p><Button variant="outline" onClick={() => { setDraft(null); setKnown(null); setConflict(false); setError(''); void query.refetch() }}>{t('recommendations.reload')}</Button></div>}
    {message && <p role="status" className="text-sm text-emerald-700 dark:text-emerald-300">{message}</p>}
    {value && content && <>
      <div className="flex flex-wrap items-center gap-x-6 gap-y-2 border-y py-3 text-xs text-muted-foreground"><span>{dirty ? t('recommendations.unsaved') : t('recommendations.savedDraft')}</span><span>{query.data?.publishedAt ? t('recommendations.publishedAt', { time: new Date(query.data.publishedAt).toLocaleString() }) : t('recommendations.defaults')}</span>{preview && <span>{t('recommendations.previewOnly')}</span>}{dirty && <Button size="sm" variant="ghost" disabled={busy} onClick={() => { setDraft(null); setKnown(null); setError('') }}>{t('systemConfig.discard')}</Button>}</div>
      <RecommendationPreview compact topicSource="draft" content={content} onManageTopics={!preview ? () => openEditor('topics') : undefined} onManageFeatured={!preview ? () => openEditor('featured') : undefined} onEditTopic={!preview ? id => openEditor('topics', id) : undefined} />
    </>}
    {mode && value && <RecommendationEditor mode={mode} initialEditingId={editId} initial={value} initialSkills={skills} onClose={closeEditor} onApply={(next, bank) => { setDraft({ value: next, version: editorVersion.current }); setKnown(bank); setKnownLocale(i18n.resolvedLanguage ?? i18n.language); closeEditor(); setMessage(''); setError('') }} />}
    <ConfirmDialog onCloseAutoFocus={event => { event.preventDefault(); publishTrigger.current?.focus() }} open={confirm} onOpenChange={setConfirm} title={t('recommendations.confirmPublish')} description={<>{t('recommendations.publishHelp')}{error && <span role="alert" className="mt-3 block text-destructive">{error}</span>}</>} confirmText={t('recommendations.publish')} closeOnConfirm={false} onConfirm={publish} />
    <ConfirmDialog open={blocker.status === 'blocked'} onOpenChange={open => { if (!open && blocker.status === 'blocked') blocker.reset() }} title={t('sitePresentation.leaveTitle')} description={t('sitePresentation.leaveHelp')} confirmText={t('sitePresentation.leaveConfirm')} onConfirm={() => { if (blocker.status === 'blocked') blocker.proceed() }} />
  </section>
}
