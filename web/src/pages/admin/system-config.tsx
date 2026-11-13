import { Globe, Tags, ShieldCheck, Send, Plus, Pencil, Shield } from 'lucide-react'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useBlocker } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ApiError, systemConfigApi } from '@/api/client'
import type { ExternalRoleGrantRule } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Switch } from '@/shared/ui/switch'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { NotificationEmailSettingsPanel } from '@/features/notification/notification-email-settings'
import { AvatarReviewSettingsPanel } from '@/features/skill/avatar-review-settings'
import { RecommendationSettingsPanel } from '@/features/recommendation/recommendation-settings'
import { SitePresentationSettingsPanel } from '@/features/site-presentation/site-presentation-settings'
import { SearchIndexMaintenance } from '@/features/governance/search-index-maintenance'

type Section = 'site' | 'recommendations' | 'access' | 'operations'
type DraftDomain = 'site' | 'recommendations' | 'avatar' | 'email'
const sections = ['site', 'recommendations', 'access', 'operations'] as const

export function SystemConfigPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: ['system-config', 'local'], queryFn: systemConfigApi.getLocalAuth })
  const rules = useQuery({ queryKey: ['system-config', 'rules'], queryFn: systemConfigApi.listRoleGrants })
  const roles = useQuery({ queryKey: ['system-config', 'roles'], queryFn: systemConfigApi.listRoles })
  const [section, setSection] = useState<Section>('site')
  const [operationsTab, setOperationsTab] = useState<'mail' | 'deliveries' | 'index'>('mail')
  const [resetKeys, setResetKeys] = useState<Record<Section, number>>({ site: 0, recommendations: 0, access: 0, operations: 0 })
  const [dirtyFlags, setDirtyFlags] = useState<Record<DraftDomain, boolean>>({ site: false, recommendations: false, avatar: false, email: false })
  const [busyFlags, setBusyFlags] = useState<Record<DraftDomain | 'index', boolean>>({ site: false, recommendations: false, avatar: false, email: false, index: false })
  const dirtyCallbacks = useMemo(() => Object.fromEntries((['site', 'recommendations', 'avatar', 'email'] as const).map(key => [key, (value: boolean) => setDirtyFlags(previous => previous[key] === value ? previous : { ...previous, [key]: value })])) as Record<DraftDomain, (value: boolean) => void>, [])
  const busyCallbacks = useMemo(() => Object.fromEntries((['site', 'recommendations', 'avatar', 'email', 'index'] as const).map(key => [key, (value: boolean) => setBusyFlags(previous => previous[key] === value ? previous : { ...previous, [key]: value })])) as Record<DraftDomain | 'index', (value: boolean) => void>, [])
  const [pendingSection, setPendingSection] = useState<Section | null>(null)
  const [providerCode, setProviderCode] = useState('')
  const [email, setEmail] = useState('')
  const [roleCode, setRoleCode] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [draft, setDraft] = useState<{ passwordLoginEnabled: boolean; selfRegistrationEnabled: boolean } | null>(null)
  const [confirmLoginOff, setConfirmLoginOff] = useState(false)
  const [ruleToDisable, setRuleToDisable] = useState<ExternalRoleGrantRule | null>(null)
  const [ruleEditor, setRuleEditor] = useState<ExternalRoleGrantRule | 'new' | null>(null)
  const [ruleError, setRuleError] = useState('')
  const initializedVersion = useRef<number | null>(null)

  useEffect(() => {
    if (settings.data && initializedVersion.current !== settings.data.version) {
      initializedVersion.current = settings.data.version
      setDraft({ passwordLoginEnabled: settings.data.passwordLoginEnabled, selfRegistrationEnabled: settings.data.selfRegistrationEnabled })
    }
  }, [settings.data])
  const refresh = useCallback(async () => { await queryClient.invalidateQueries({ queryKey: ['system-config'] }) }, [queryClient])
  const updateSettings = useMutation({
    mutationFn: systemConfigApi.updateLocalAuth,
    onSuccess: async value => {
      queryClient.setQueryData(['auth', 'local-capabilities'], { passwordLoginEnabled: value.passwordLoginEnabled, selfRegistrationEnabled: value.selfRegistrationEnabled, registrationAvailable: value.passwordLoginEnabled && value.selfRegistrationEnabled })
      queryClient.setQueryData(['system-config', 'local'], value)
    },
  })
  const createRule = useMutation({ mutationFn: systemConfigApi.createRoleGrant, onSuccess: refresh })
  const updateRule = useMutation({ mutationFn: ({ id, version, code }: { id: number; version: number; code: string }) => systemConfigApi.updateRoleGrant(id, { version, roleCode: code }), onSuccess: refresh })
  const disableRule = useMutation({ mutationFn: ({ id, version }: { id: number; version: number }) => systemConfigApi.disableRoleGrant(id, version), onSuccess: refresh })
  const settingsDirty = !!settings.data && !!draft && (settings.data.passwordLoginEnabled !== draft.passwordLoginEnabled || settings.data.selfRegistrationEnabled !== draft.selfRegistrationEnabled)
  const ruleDirty = ruleEditor === 'new' ? !!providerCode || !!email || roleCode !== (roles.data?.[0]?.code ?? '') : !!ruleEditor && roleCode !== ruleEditor.roleCode
  const busy = updateSettings.isPending || createRule.isPending || updateRule.isPending || disableRule.isPending || Object.values(busyFlags).some(Boolean)
  const localDirty = settingsDirty || dirtyFlags.avatar || dirtyFlags.email || ruleDirty
  const localBusy = updateSettings.isPending || createRule.isPending || updateRule.isPending || disableRule.isPending || busyFlags.avatar || busyFlags.email || busyFlags.index
  const blocker = useBlocker({ shouldBlockFn: () => localDirty || localBusy, enableBeforeUnload: localDirty || localBusy, withResolver: true })
  const currentDirty = section === 'site' ? dirtyFlags.site : section === 'recommendations' ? dirtyFlags.recommendations : section === 'access' ? settingsDirty || dirtyFlags.avatar || ruleDirty : dirtyFlags.email

  async function run(action: () => Promise<unknown>): Promise<boolean> {
    setError(''); setMessage('')
    try { await action(); setMessage(t('systemConfig.saved')); return true }
    catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) { setConfirmLoginOff(false); setRuleToDisable(null); setRuleEditor(null); await refresh(); setError(t('systemConfig.changedElsewhere')); return false }
      setError(cause instanceof Error ? cause.message : t('systemConfig.saveFailed')); return false
    }
  }
  async function saveSettings() {
    if (!settings.data || !draft || updateSettings.isPending || !settingsDirty) return false
    return run(() => updateSettings.mutateAsync({ ...draft, version: settings.data!.version }))
  }
  function changeSection(next: Section) { if (busy || next === section) return; if (currentDirty) setPendingSection(next); else { setSection(next); setError(''); setMessage('') } }
  function discardSection() {
    if (!pendingSection || busy) return
    setResetKeys(current => ({ ...current, [section]: current[section] + 1 }))
    if (section === 'access' && settings.data) { setDraft({ passwordLoginEnabled: settings.data.passwordLoginEnabled, selfRegistrationEnabled: settings.data.selfRegistrationEnabled }); setRuleEditor(null) }
    setDirtyFlags(current => ({ ...current, ...(section === 'site' ? { site: false } : section === 'recommendations' ? { recommendations: false } : section === 'access' ? { avatar: false } : { email: false }) }))
    setSection(pendingSection); setPendingSection(null); setError(''); setMessage('')
  }
  function openRule(rule: ExternalRoleGrantRule | 'new') {
    setRuleEditor(rule); setRuleError(''); setProviderCode(rule === 'new' ? '' : rule.providerCode); setEmail(rule === 'new' ? '' : rule.email); setRoleCode(rule === 'new' ? (roles.data?.[0]?.code ?? '') : rule.roleCode)
  }
  async function saveRule() {
    if (!ruleEditor || createRule.isPending || updateRule.isPending) return
    setRuleError('')
    try {
      if (ruleEditor === 'new') await createRule.mutateAsync({ providerCode: providerCode.trim().toLowerCase(), email: email.trim().toLowerCase(), roleCode })
      else await updateRule.mutateAsync({ id: ruleEditor.id, version: ruleEditor.version, code: roleCode })
      setRuleEditor(null); setMessage(t('systemConfig.saved'))
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) { await refresh(); setRuleEditor(null); setError(t('systemConfig.changedElsewhere')) }
      else setRuleError(cause instanceof Error ? cause.message : t('systemConfig.saveFailed'))
    }
  }
  const ruleBusy = createRule.isPending || updateRule.isPending

  return <div className="grid items-start gap-8 pb-12 lg:grid-cols-[180px_minmax(0,1fr)]">
    <aside className="space-y-6 border-b pb-5 lg:sticky lg:top-24 lg:border-b-0 lg:border-r lg:pr-5"><div className="space-y-2"><h1 className="text-2xl font-semibold">{t('systemConfig.title')}</h1><p className="text-sm leading-5 text-muted-foreground">{t('systemConfig.intro')}</p></div><nav aria-label={t('recommendations.settingsNavigation')} className="flex flex-wrap gap-1 lg:flex-col lg:items-stretch">{sections.map(id => { const Icon = { site: Globe, recommendations: Tags, access: ShieldCheck, operations: Send }[id]; return <Button key={id} variant={section === id ? 'secondary' : 'ghost'} className="justify-start" aria-pressed={section === id} disabled={busy} onClick={() => changeSection(id)}><Icon aria-hidden className="h-4 w-4 shrink-0" />{t(`recommendations.navigation.${id}`)}</Button> })}</nav><p className="hidden border-t pt-4 text-xs leading-5 text-muted-foreground lg:block">{t('systemConfig.independentSettings')}</p></aside>
    <div className="min-w-0 space-y-6">
      {error && <p role="alert" className="rounded-lg bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</p>}
      {message && <p role="status" className="rounded-lg bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700 dark:text-emerald-300">{message}</p>}
      <div hidden={section !== 'site'}><SitePresentationSettingsPanel key={resetKeys.site} onDirtyChange={dirtyCallbacks.site} onBusyChange={busyCallbacks.site} /></div>
      <div hidden={section !== 'recommendations'}><RecommendationSettingsPanel key={resetKeys.recommendations} onDirtyChange={dirtyCallbacks.recommendations} onBusyChange={busyCallbacks.recommendations} /></div>
      <div hidden={section !== 'operations'} className="space-y-6"><header><h2 className="text-2xl font-semibold">{t('recommendations.navigation.operations')}</h2><p className="mt-1 text-sm text-muted-foreground">{t('systemConfig.operationsHelp')}</p></header><div role="group" aria-label={t('systemConfig.operationsNavigation')} className="inline-flex max-w-full flex-wrap gap-1 rounded-full border bg-muted/50 p-1">{(['mail', 'deliveries', 'index'] as const).map(tab => <Button key={tab} variant="ghost" aria-pressed={operationsTab === tab} disabled={busy} className={`rounded-full px-4 ${operationsTab === tab ? 'bg-background shadow-sm hover:bg-background' : 'text-muted-foreground'}`} onClick={() => setOperationsTab(tab)}>{t(`systemConfig.operationsTab.${tab}`)}</Button>)}</div><div key={resetKeys.operations} hidden={operationsTab === 'index'}><NotificationEmailSettingsPanel view={operationsTab === 'deliveries' ? 'deliveries' : 'mail'} onViewDeliveries={() => setOperationsTab('deliveries')} onDirtyChange={dirtyCallbacks.email} onBusyChange={busyCallbacks.email} /></div><div hidden={operationsTab !== 'index'}><SearchIndexMaintenance onBusyChange={busyCallbacks.index} /></div></div>
      <div hidden={section !== 'access'} className="space-y-8"><header><h2 className="text-2xl font-semibold">{t('recommendations.navigation.access')}</h2><p className="mt-1 text-sm text-muted-foreground">{t('systemConfig.accessHelp')}</p></header><div className="grid items-stretch gap-6 xl:grid-cols-2"><section className="flex min-w-0 flex-col rounded-xl border p-5"><header className="mb-6"><h3 className="text-lg font-semibold">{t('systemConfig.localAuth')}</h3><p className="mt-1 text-sm leading-5 text-muted-foreground">{t('systemConfig.lockoutHelp')}</p></header>
        {settings.isError && <p role="alert">{t('systemConfig.loadFailed')}</p>}{settings.isPending && <p role="status">{t('systemConfig.loading')}</p>}
        {settings.data && draft && <><div className="divide-y">{([['passwordLoginEnabled', 'passwordLogin', 'passwordLoginHelp'], ['selfRegistrationEnabled', 'selfRegistration', 'selfRegistrationHelp']] as const).map(([field, title, help]) => <div key={field} className="flex items-center justify-between gap-6 py-4"><div className="min-w-0 space-y-1"><Label htmlFor={field}>{t(`systemConfig.${title}`)}</Label><p id={`${field}-help`} className="text-xs leading-5 text-muted-foreground">{t(`systemConfig.${help}`)}</p></div><Switch id={field} aria-describedby={`${field}-help`} data-analytics-id={field === 'passwordLoginEnabled' ? 'admin.auth.password.toggle' : 'admin.auth.registration.toggle'} checked={draft[field]} disabled={updateSettings.isPending} onCheckedChange={value => setDraft({ ...draft, [field]: value })} /></div>)}</div>{!draft.passwordLoginEnabled && draft.selfRegistrationEnabled && <p className="mb-4 text-sm text-muted-foreground">{t('systemConfig.registrationRequiresLogin')}</p>}<footer className="mt-auto flex flex-wrap items-center justify-between gap-3 border-t pt-4"><span className="text-xs text-muted-foreground">{t(settingsDirty ? 'systemConfig.unsaved' : 'systemConfig.upToDate')}</span><div className="flex gap-2"><Button data-analytics-id="admin.auth.discard" variant="outline" disabled={!settingsDirty || updateSettings.isPending} onClick={() => { setDraft({ passwordLoginEnabled: settings.data!.passwordLoginEnabled, selfRegistrationEnabled: settings.data!.selfRegistrationEnabled }); setError('') }}>{t('systemConfig.discard')}</Button><Button data-analytics-id="admin.auth.save" disabled={!settingsDirty || updateSettings.isPending} onClick={() => { if (draft.passwordLoginEnabled === false && settings.data!.passwordLoginEnabled) setConfirmLoginOff(true); else void saveSettings() }}>{t(updateSettings.isPending ? 'systemConfig.saving' : 'systemConfig.saveChanges')}</Button></div></footer></>}
      </section><AvatarReviewSettingsPanel key={resetKeys.access} onDirtyChange={dirtyCallbacks.avatar} onBusyChange={busyCallbacks.avatar} /></div>
      <section className="space-y-5 border-t pt-6"><div className="flex flex-wrap items-start justify-between gap-3"><div><h3 className="text-lg font-semibold">{t('systemConfig.initialRoleRules')}</h3><p className="mt-1 text-sm text-muted-foreground">{t('systemConfig.ruleApplies')}</p></div><Button data-analytics-id="admin.role.rule.add" disabled={!roles.data?.length || busy} onClick={() => openRule('new')}><Plus aria-hidden className="h-4 w-4" />{t('systemConfig.addRule')}</Button></div><p className="border-l-2 bg-muted/40 px-4 py-3 text-xs leading-5 text-muted-foreground">{t('systemConfig.externalRulesNotice')}</p>{rules.isError || roles.isError ? <p role="alert">{t('systemConfig.loadFailed')}</p> : null}{rules.isPending ? <p role="status">{t('systemConfig.loading')}</p> : null}{rules.data?.length === 0 && <p className="rounded-lg border border-dashed p-6 text-center text-sm text-muted-foreground">{t('systemConfig.noRules')}</p>}{!!rules.data?.length && <div className="overflow-x-auto"><table className="w-full min-w-[600px] text-left text-sm"><thead className="bg-muted/50 text-xs text-muted-foreground"><tr>{['accountSource', 'role', 'ruleStatus', 'actions'].map(key => <th key={key} className="px-3 py-3 font-medium">{t(`systemConfig.${key}`)}</th>)}</tr></thead><tbody>{rules.data.map(rule => <tr key={rule.id} className="border-b"><td className="max-w-80 break-all px-3 py-4"><p className="font-medium">{rule.email}</p><p className="mt-1 text-xs text-muted-foreground">{rule.providerCode}</p>{rule.status === 'CONSUMED' && <p className="mt-1 text-xs text-muted-foreground">{t('systemConfig.grantedTo', { userId: rule.grantedUserId, subject: rule.matchedSubject })}</p>}</td><td className="px-3 py-4">{roles.data?.find(role => role.code === rule.roleCode)?.name ?? rule.roleCode}</td><td className="px-3 py-4"><span className={`whitespace-nowrap rounded-md px-2 py-1 text-xs ${rule.status === 'ACTIVE' ? 'bg-blue-500/10 text-blue-700 dark:text-blue-300' : rule.status === 'CONSUMED' ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-300' : 'bg-muted text-muted-foreground'}`}>{t(`systemConfig.status.${rule.status}`)}</span></td><td className="px-3 py-4">{rule.status === 'ACTIVE' ? <div className="flex gap-1"><Button data-analytics-id="admin.role.rule.edit.open" size="xs" variant="ghost" disabled={busy} onClick={() => openRule(rule)}><Pencil aria-hidden className="h-3.5 w-3.5" />{t('systemConfig.editRule')}</Button><Button data-analytics-id="admin.role.rule.disable" size="xs" variant="ghost" disabled={busy} onClick={() => setRuleToDisable(rule)}>{t('systemConfig.disable')}</Button></div> : <span className="text-xs text-muted-foreground">{t('systemConfig.readOnly')}</span>}</td></tr>)}</tbody></table></div>}</section>
      </div>
    </div>
    <Dialog open={!!ruleEditor} onOpenChange={open => { if (!open && !ruleBusy) setRuleEditor(null) }}><DialogContent hideClose={ruleBusy} className="w-[min(calc(100vw-2rem),55rem)]" onEscapeKeyDown={event => { if (ruleBusy) event.preventDefault() }} onPointerDownOutside={event => { if (ruleBusy) event.preventDefault() }}><DialogHeader><DialogTitle>{t(ruleEditor === 'new' ? 'systemConfig.addRule' : 'systemConfig.editRule')}</DialogTitle><DialogDescription>{t('systemConfig.ruleApplies')}</DialogDescription></DialogHeader><form data-analytics-id={ruleEditor === 'new' ? 'admin.role.rule.create' : undefined} onSubmit={event => { event.preventDefault(); void saveRule() }}><fieldset disabled={ruleBusy} className="space-y-5"><div className="rounded-lg bg-muted/50 p-4"><Shield aria-hidden className="mb-2 h-5 w-5 text-muted-foreground" /><p className="text-sm leading-6">{t('systemConfig.ruleNoMerge')}</p></div><div className="grid gap-4 sm:grid-cols-2"><div className="space-y-1.5"><Label htmlFor="role-grant-provider">{t('systemConfig.provider')}</Label><Input data-analytics-id="admin.role.rule.provider.input" id="role-grant-provider" required maxLength={64} pattern="[a-z0-9][a-z0-9_-]{0,63}" value={providerCode} readOnly={ruleEditor !== 'new'} onChange={event => setProviderCode(event.target.value.toLowerCase())} onBlur={() => setProviderCode(value => value.trim())} placeholder="github" /></div><div className="space-y-1.5"><Label htmlFor="role-grant-email">{t('systemConfig.email')}</Label><Input data-analytics-id="admin.role.rule.email.input" id="role-grant-email" type="email" required value={email} readOnly={ruleEditor !== 'new'} onChange={event => setEmail(event.target.value)} onBlur={() => setEmail(value => value.trim().toLowerCase())} /></div></div><div className="space-y-1.5"><Label htmlFor="role-grant-role">{t('systemConfig.role')}</Label><Select value={roleCode} onValueChange={setRoleCode} disabled={ruleBusy}><SelectTrigger data-analytics-id={ruleEditor === 'new' ? 'admin.role.rule.role.select' : 'admin.role.rule.role.edit'} id="role-grant-role"><SelectValue /></SelectTrigger><SelectContent>{(roles.data ?? []).map(role => <SelectItem key={role.code} value={role.code}>{role.name}</SelectItem>)}</SelectContent></Select></div>{ruleError && <p role="alert" className="text-sm text-destructive">{ruleError}</p>}<DialogFooter className="justify-end border-t pt-4 sm:justify-end"><Button data-analytics-id="admin.role.rule.discard" type="button" variant="outline" onClick={() => setRuleEditor(null)}>{t('dialog.cancel')}</Button><Button data-analytics-id={ruleEditor === 'new' ? 'admin.role.rule.create' : 'admin.role.rule.save'} type="submit" disabled={!roles.data?.length || (ruleEditor !== 'new' && !ruleDirty)}>{t(ruleBusy ? 'systemConfig.saving' : 'systemConfig.saveChanges')}</Button></DialogFooter></fieldset></form></DialogContent></Dialog>
    <ConfirmDialog open={pendingSection !== null} onOpenChange={open => { if (!open) setPendingSection(null) }} title={t('sitePresentation.leaveTitle')} description={t('sitePresentation.leaveHelp')} confirmText={t('sitePresentation.leaveConfirm')} onConfirm={discardSection} />
    <ConfirmDialog open={blocker.status === 'blocked'} onOpenChange={open => { if (!open && blocker.status === 'blocked') blocker.reset() }} title={t('sitePresentation.leaveTitle')} description={t('sitePresentation.leaveHelp')} confirmText={t('sitePresentation.leaveConfirm')} onConfirm={() => { if (blocker.status === 'blocked') blocker.proceed() }} />
    <ConfirmDialog confirmAnalyticsId="admin.auth.login.disable.confirm" cancelAnalyticsId="admin.auth.login.disable.cancel" open={confirmLoginOff} onOpenChange={setConfirmLoginOff} title={t('systemConfig.lockoutTitle')} description={<>{t('systemConfig.lockoutWarning')}{error && <span role="alert" className="mt-3 block text-destructive">{error}</span>}</>} confirmText={t('systemConfig.confirmDisableLogin')} closeOnConfirm={false} onConfirm={async () => { if (await saveSettings()) setConfirmLoginOff(false) }} />
    <ConfirmDialog confirmAnalyticsId="admin.role.rule.disable.confirm" cancelAnalyticsId="admin.role.rule.disable.cancel" open={!!ruleToDisable} onOpenChange={open => { if (!open) setRuleToDisable(null) }} closeOnConfirm={false} title={t('systemConfig.disableTitle')} description={<>{t('systemConfig.disableConfirm')}{error && <span role="alert" className="mt-3 block text-destructive">{error}</span>}</>} confirmText={t('systemConfig.disable')} variant="destructive" onConfirm={async () => { if (ruleToDisable && await run(() => disableRule.mutateAsync({ id: ruleToDisable.id, version: ruleToDisable.version }))) setRuleToDisable(null) }} />
  </div>
}
