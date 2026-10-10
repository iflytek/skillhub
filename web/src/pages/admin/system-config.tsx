import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { systemConfigApi } from '@/api/client'
import type { ExternalRoleGrantRule } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'

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

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['system-config'] })
  }
  const updateSettings = useMutation({ mutationFn: systemConfigApi.updateLocalAuth, onSuccess: refresh })
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

  async function run(action: () => Promise<unknown>) {
    setError('')
    setMessage('')
    try {
      await action()
      setMessage(t('systemConfig.saved'))
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : t('systemConfig.saveFailed'))
    }
  }

  function changeSetting(field: 'passwordLoginEnabled' | 'selfRegistrationEnabled', checked: boolean) {
    if (!settings.data) return
    if (field === 'passwordLoginEnabled' && !checked && !window.confirm(t('systemConfig.lockoutWarning'))) return
    void run(() => updateSettings.mutateAsync({
      passwordLoginEnabled: field === 'passwordLoginEnabled' ? checked : settings.data!.passwordLoginEnabled,
      selfRegistrationEnabled: field === 'selfRegistrationEnabled' ? checked : settings.data!.selfRegistrationEnabled,
      version: settings.data!.version,
    }))
  }

  function changeRole(rule: ExternalRoleGrantRule, code: string) {
    if (code === rule.roleCode) return
    void run(() => updateRule.mutateAsync({ id: rule.id, version: rule.version, code }))
  }

  return (
    <div className="mx-auto max-w-4xl space-y-6 p-4 sm:p-8">
      <h1 className="text-2xl font-semibold">{t('systemConfig.title')}</h1>
      {error ? <p role="alert" className="text-sm text-red-600">{error}</p> : null}
      {message ? <p role="status" className="text-sm text-emerald-700">{message}</p> : null}
      <Card>
        <CardHeader><CardTitle>{t('systemConfig.localAuth')}</CardTitle></CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-muted-foreground">{t('systemConfig.lockoutHelp')}</p>
          {settings.isError ? <p role="alert">{t('systemConfig.loadFailed')}</p> : null}
          {settings.data ? (
            <>
              <label className="flex items-center gap-3">
                <input type="checkbox" checked={settings.data.passwordLoginEnabled} disabled={updateSettings.isPending}
                  onChange={(event) => changeSetting('passwordLoginEnabled', event.target.checked)} />
                {t('systemConfig.passwordLogin')}
              </label>
              <label className="flex items-center gap-3">
                <input type="checkbox" checked={settings.data.selfRegistrationEnabled} disabled={updateSettings.isPending}
                  onChange={(event) => changeSetting('selfRegistrationEnabled', event.target.checked)} />
                {t('systemConfig.selfRegistration')}
              </label>
              {!settings.data.passwordLoginEnabled && settings.data.selfRegistrationEnabled ? (
                <p className="text-sm text-muted-foreground">{t('systemConfig.registrationRequiresLogin')}</p>
              ) : null}
            </>
          ) : null}
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>{t('systemConfig.initialRoleRules')}</CardTitle></CardHeader>
        <CardContent className="space-y-5">
          <p className="text-sm text-muted-foreground">{t('systemConfig.ruleHelp')}</p>
          <form className="grid gap-3 sm:grid-cols-4" onSubmit={(event) => {
            event.preventDefault()
            void run(async () => {
              await createRule.mutateAsync({ providerCode, email, roleCode })
              setProviderCode('')
              setEmail('')
            })
          }}>
            <Input aria-label={t('systemConfig.provider')} placeholder={t('systemConfig.provider')} value={providerCode}
              onChange={(event) => setProviderCode(event.target.value)} required />
            <Input aria-label={t('systemConfig.email')} placeholder={t('systemConfig.email')} type="email" value={email}
              onChange={(event) => setEmail(event.target.value)} required />
            <select aria-label={t('systemConfig.role')} className="rounded-md border bg-background px-3" value={roleCode}
              onChange={(event) => setRoleCode(event.target.value)}>
              {(roles.data ?? []).map((role) => <option key={role.code} value={role.code}>{role.name}</option>)}
            </select>
            <Button type="submit" disabled={createRule.isPending || !roles.data?.length}>{t('systemConfig.addRule')}</Button>
          </form>
          {rules.isError || roles.isError ? <p role="alert">{t('systemConfig.loadFailed')}</p> : null}
          <div className="space-y-3">
            {(rules.data ?? []).map((rule) => (
              <div key={rule.id} className="flex flex-wrap items-center gap-3 rounded-md border p-3">
                <span className="min-w-0 flex-1 break-all text-sm">{rule.providerCode} · {rule.email}</span>
                <span className="text-xs text-muted-foreground">{t(`systemConfig.status.${rule.status}`)}</span>
                {rule.status === 'CONSUMED' ? (
                  <span className="w-full break-all text-xs text-muted-foreground">
                    {t('systemConfig.grantedTo', { userId: rule.grantedUserId, subject: rule.matchedSubject })}
                  </span>
                ) : null}
                {rule.status === 'ACTIVE' ? (
                  <>
                    <select aria-label={t('systemConfig.role')} className="rounded-md border bg-background px-2 py-1"
                      value={rule.roleCode} disabled={updateRule.isPending}
                      onChange={(event) => changeRole(rule, event.target.value)}>
                      {(roles.data ?? []).map((role) => <option key={role.code} value={role.code}>{role.name}</option>)}
                    </select>
                    <Button type="button" variant="outline" disabled={disableRule.isPending} onClick={() => {
                      if (window.confirm(t('systemConfig.disableConfirm'))) {
                        void run(() => disableRule.mutateAsync({ id: rule.id, version: rule.version }))
                      }
                    }}>{t('systemConfig.disable')}</Button>
                  </>
                ) : <span className="text-sm">{rule.roleCode}</span>}
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
