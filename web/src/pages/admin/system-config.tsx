import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ApiError, systemConfigApi } from '@/api/client'
import type { ExternalRoleGrantRule } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'

export function SystemConfigPage() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const settings = useQuery({ queryKey: ['system-config', 'local'], queryFn: systemConfigApi.getLocalAuth })
  const rules = useQuery({ queryKey: ['system-config', 'rules'], queryFn: systemConfigApi.listRoleGrants })
  const roles = useQuery({ queryKey: ['system-config', 'roles'], queryFn: systemConfigApi.listRoles })
  const [providerCode, setProviderCode] = useState('')
  const [email, setEmail] = useState('')
  const [roleCode, setRoleCode] = useState('SUPER_ADMIN')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [draft, setDraft] = useState<{ passwordLoginEnabled: boolean; selfRegistrationEnabled: boolean } | null>(null)
  const [confirmLoginOff, setConfirmLoginOff] = useState(false)
  const [ruleToDisable, setRuleToDisable] = useState<ExternalRoleGrantRule | null>(null)
  const [ruleDrafts, setRuleDrafts] = useState<Record<number, string>>({})
  const initializedVersion = useRef<number | null>(null)

  useEffect(() => {
    if (settings.data && initializedVersion.current !== settings.data.version) {
      initializedVersion.current = settings.data.version
      setDraft({ passwordLoginEnabled: settings.data.passwordLoginEnabled, selfRegistrationEnabled: settings.data.selfRegistrationEnabled })
    }
  }, [settings.data])

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['system-config'] })
  }
  const updateSettings = useMutation({
    mutationFn: systemConfigApi.updateLocalAuth,
    onSuccess: async (value) => {
      queryClient.setQueryData(['auth', 'local-capabilities'], {
        passwordLoginEnabled: value.passwordLoginEnabled,
        selfRegistrationEnabled: value.selfRegistrationEnabled,
        registrationAvailable: value.passwordLoginEnabled && value.selfRegistrationEnabled,
      })
      await refresh()
    },
  })
  const createRule = useMutation({ mutationFn: systemConfigApi.createRoleGrant, onSuccess: refresh })
  const updateRule = useMutation({
    mutationFn: ({ id, version, code }: { id: number; version: number; code: string }) =>
      systemConfigApi.updateRoleGrant(id, { version, roleCode: code }),
    onSuccess: refresh,
  })
  const disableRule = useMutation({
    mutationFn: ({ id, version }: { id: number; version: number }) => systemConfigApi.disableRoleGrant(id, version),
    onSuccess: refresh,
  })

  async function run(action: () => Promise<unknown>): Promise<boolean> {
    setError('')
    setMessage('')
    try {
      await action()
      setMessage(t('systemConfig.saved'))
      return true
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 409) {
        setConfirmLoginOff(false)
        setRuleToDisable(null)
        await refresh()
        setError(t('systemConfig.changedElsewhere'))
        return false
      }
      setError(cause instanceof Error ? cause.message : t('systemConfig.saveFailed'))
      return false
    }
  }

  async function saveSettings() {
    if (!settings.data || !draft || updateSettings.isPending) return false
    return run(() => updateSettings.mutateAsync({ ...draft, version: settings.data!.version }))
  }

  const settingsDirty = !!settings.data && !!draft && (
    settings.data.passwordLoginEnabled !== draft.passwordLoginEnabled
    || settings.data.selfRegistrationEnabled !== draft.selfRegistrationEnabled
  )

  function changeRole(rule: ExternalRoleGrantRule) {
    const code = ruleDrafts[rule.id]
    if (!code || code === rule.roleCode) return
    void run(async () => {
      await updateRule.mutateAsync({ id: rule.id, version: rule.version, code })
      setRuleDrafts((current) => ({ ...current, [rule.id]: code }))
    })
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-4 pb-12 sm:p-8">
      <div className="space-y-2">
        <h1 className="text-2xl font-semibold">{t('systemConfig.title')}</h1>
        <p className="text-sm text-muted-foreground">{t('systemConfig.intro')}</p>
      </div>
      {error ? <p role="alert" className="rounded-lg bg-destructive/10 px-4 py-3 text-sm text-destructive">{error}</p> : null}
      {message ? <p role="status" className="rounded-lg bg-emerald-500/10 px-4 py-3 text-sm text-emerald-700">{message}</p> : null}
      <Card>
        <CardHeader>
          <CardTitle>{t('systemConfig.localAuth')}</CardTitle>
          <p className="text-sm text-muted-foreground">{t('systemConfig.lockoutHelp')}</p>
        </CardHeader>
        <CardContent className="space-y-4">
          {settings.isError ? <p role="alert">{t('systemConfig.loadFailed')}</p> : null}
          {settings.isPending ? <p className="text-sm text-muted-foreground">{t('systemConfig.loading')}</p> : null}
          {settings.data && draft ? (
            <>
              <div className="divide-y rounded-xl border border-border/60">
                {([
                  ['passwordLoginEnabled', 'passwordLogin', 'passwordLoginHelp'],
                  ['selfRegistrationEnabled', 'selfRegistration', 'selfRegistrationHelp'],
                ] as const).map(([field, title, help]) => (
                  <label key={field} className="flex cursor-pointer items-center justify-between gap-6 p-4 sm:p-5">
                    <span className="min-w-0 space-y-1">
                      <span className="block text-sm font-medium">{t(`systemConfig.${title}`)}</span>
                      <span className="block text-sm text-muted-foreground">{t(`systemConfig.${help}`)}</span>
                    </span>
                    <span className="relative shrink-0">
                      <input type="checkbox" className="peer sr-only" checked={draft[field]} disabled={updateSettings.isPending}
                        onChange={(event) => setDraft({ ...draft, [field]: event.target.checked })}
                        aria-label={t(`systemConfig.${title}`)} />
                      <span aria-hidden="true" className="block h-6 w-11 rounded-full bg-muted-foreground/35 transition-colors peer-checked:bg-primary peer-focus-visible:ring-4 peer-focus-visible:ring-ring/30 peer-disabled:opacity-50" />
                      <span aria-hidden="true" className="pointer-events-none absolute left-0.5 top-0.5 h-5 w-5 rounded-full bg-white shadow-sm transition-transform peer-checked:translate-x-5" />
                    </span>
                  </label>
                ))}
              </div>
              {!draft.passwordLoginEnabled && draft.selfRegistrationEnabled ? (
                <p className="rounded-lg bg-amber-500/10 px-4 py-3 text-sm text-amber-800 dark:text-amber-200">{t('systemConfig.registrationRequiresLogin')}</p>
              ) : null}
              <div className="flex flex-wrap items-center justify-end gap-3 border-t pt-4">
                {settingsDirty ? <span role="status" className="mr-auto text-sm text-muted-foreground">{t('systemConfig.unsaved')}</span> : null}
                <Button type="button" variant="outline" disabled={!settingsDirty || updateSettings.isPending}
                  onClick={() => setDraft({ passwordLoginEnabled: settings.data!.passwordLoginEnabled, selfRegistrationEnabled: settings.data!.selfRegistrationEnabled })}>
                  {t('systemConfig.discard')}
                </Button>
                <Button type="button" disabled={!settingsDirty || updateSettings.isPending} onClick={() => {
                  if (settings.data!.passwordLoginEnabled && !draft.passwordLoginEnabled) setConfirmLoginOff(true)
                  else void saveSettings()
                }}>{updateSettings.isPending ? t('systemConfig.saving') : t('systemConfig.saveChanges')}</Button>
              </div>
            </>
          ) : null}
        </CardContent>
      </Card>
      <ConfirmDialog open={confirmLoginOff} onOpenChange={setConfirmLoginOff}
        closeOnConfirm={false}
        title={t('systemConfig.lockoutTitle')} description={<>{t('systemConfig.lockoutWarning')}{error ? <span role="alert" className="mt-3 block text-destructive">{error}</span> : null}</>}
        confirmText={t('systemConfig.confirmDisableLogin')} variant="destructive"
        onConfirm={async () => { if (await saveSettings()) setConfirmLoginOff(false) }} />
      <Card>
        <CardHeader>
          <CardTitle>{t('systemConfig.initialRoleRules')}</CardTitle>
          <p className="text-sm text-muted-foreground">{t('systemConfig.ruleIntro')}</p>
        </CardHeader>
        <CardContent className="space-y-5">
          <ul className="list-disc space-y-1 rounded-lg bg-muted/60 px-8 py-3 text-sm leading-relaxed text-muted-foreground">
            <li>{t('systemConfig.ruleApplies')}</li>
            <li>{t('systemConfig.ruleNoMerge')}</li>
            <li>{t('systemConfig.ruleUnavailable')}</li>
          </ul>
          <form className="grid items-end gap-4 rounded-xl border border-border/60 bg-muted/20 p-4 sm:grid-cols-2 lg:grid-cols-[1fr_1.2fr_1fr_auto]" onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await createRule.mutateAsync({ providerCode: providerCode.trim(), email: email.trim(), roleCode })
              setProviderCode('')
              setEmail('')
            })
          }}>
            <div className="space-y-1.5"><Label htmlFor="role-grant-provider">{t('systemConfig.provider')}</Label>
              <Input id="role-grant-provider" placeholder={t('systemConfig.providerExample')} value={providerCode}
                onChange={(event) => setProviderCode(event.target.value)} required maxLength={64} /></div>
            <div className="space-y-1.5"><Label htmlFor="role-grant-email">{t('systemConfig.email')}</Label>
              <Input id="role-grant-email" placeholder="admin@example.com" type="email" value={email}
                onChange={(event) => setEmail(event.target.value)} required /></div>
            <div className="space-y-1.5"><Label htmlFor="role-grant-role">{t('systemConfig.role')}</Label>
              <select id="role-grant-role" className="h-9 w-full rounded-md border border-border/60 bg-background px-3 text-sm" value={roleCode}
              onChange={(event) => setRoleCode(event.target.value)}>
              {(roles.data ?? []).map((role) => <option key={role.code} value={role.code}>{role.name}</option>)}
              </select></div>
            <Button type="submit" disabled={createRule.isPending || !roles.data?.length}>{createRule.isPending ? t('systemConfig.saving') : t('systemConfig.addRule')}</Button>
          </form>
          {rules.isError || roles.isError ? <p role="alert">{t('systemConfig.loadFailed')}</p> : null}
          <h3 className="text-sm font-semibold">{t('systemConfig.existingRules')}</h3>
          {rules.isPending ? <p className="text-sm text-muted-foreground">{t('systemConfig.loading')}</p> : null}
          {rules.data?.length === 0 ? <p className="rounded-xl border border-dashed p-6 text-center text-sm text-muted-foreground">{t('systemConfig.noRules')}</p> : null}
          <div className="space-y-3">
            {(rules.data ?? []).map((rule) => (
              <div key={rule.id} className="space-y-3 rounded-xl border border-border/60 p-4">
                <div className="flex flex-wrap items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="break-all text-sm font-medium">{rule.email}</p>
                    <p className="mt-1 text-xs text-muted-foreground">{t('systemConfig.provider')}: {rule.providerCode}</p>
                  </div>
                  <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-secondary-foreground">{t(`systemConfig.status.${rule.status}`)}</span>
                </div>
                {rule.status === 'CONSUMED' ? (
                  <p className="break-all text-xs text-muted-foreground">
                    {t('systemConfig.grantedTo', { userId: rule.grantedUserId, subject: rule.matchedSubject })}
                  </p>
                ) : null}
                {rule.status === 'ACTIVE' ? (
                  <div className="flex flex-wrap items-end gap-2 border-t pt-3">
                    <div className="min-w-40 flex-1 space-y-1.5 sm:flex-none">
                    <Label htmlFor={`rule-role-${rule.id}`}>{t('systemConfig.role')}</Label>
                    <select id={`rule-role-${rule.id}`} className="h-9 w-full rounded-md border border-border/60 bg-background px-3 text-sm"
                      value={ruleDrafts[rule.id] ?? rule.roleCode} disabled={updateRule.isPending}
                      onChange={(event) => setRuleDrafts({ ...ruleDrafts, [rule.id]: event.target.value })}>
                      {(roles.data ?? []).map((role) => <option key={role.code} value={role.code}>{role.name}</option>)}
                    </select>
                    </div>
                    {ruleDrafts[rule.id] && ruleDrafts[rule.id] !== rule.roleCode ? (
                      <>
                        <Button type="button" size="sm" variant="outline" disabled={updateRule.isPending} onClick={() => setRuleDrafts({ ...ruleDrafts, [rule.id]: rule.roleCode })}>{t('systemConfig.discard')}</Button>
                        <Button type="button" size="sm" disabled={updateRule.isPending} onClick={() => changeRole(rule)}>{t('systemConfig.saveChanges')}</Button>
                      </>
                    ) : null}
                    <Button type="button" size="sm" variant="ghost" className="sm:ml-auto" disabled={disableRule.isPending}
                      onClick={() => setRuleToDisable(rule)}>{t('systemConfig.disable')}</Button>
                  </div>
                ) : <p className="text-xs text-muted-foreground">{t('systemConfig.role')}: {roles.data?.find((role) => role.code === rule.roleCode)?.name ?? rule.roleCode}</p>}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
      <ConfirmDialog open={!!ruleToDisable} onOpenChange={(open) => { if (!open) setRuleToDisable(null) }}
        closeOnConfirm={false}
        title={t('systemConfig.disableTitle')} description={<>{t('systemConfig.disableConfirm')}{error ? <span role="alert" className="mt-3 block text-destructive">{error}</span> : null}</>}
        confirmText={t('systemConfig.disable')} variant="destructive"
        onConfirm={async () => {
          if (ruleToDisable && await run(() => disableRule.mutateAsync({ id: ruleToDisable.id, version: ruleToDisable.version }))) {
            setRuleToDisable(null)
          }
        }} />
    </div>
  )
}
