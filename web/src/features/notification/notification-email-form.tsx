import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { Link } from '@tanstack/react-router'
import { Button } from '@/shared/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { EMAIL_CATEGORIES, type EmailCategory, type EmailMode } from './notification-email-api'
import { useNotificationEmail } from './use-notification-email'

export function NotificationEmailForm() {
  const { user } = useAuth()
  return <NotificationEmailContactFields key={user?.userId ?? 'guest'} />
}

function NotificationEmailContactFields() {
  const { t, i18n } = useTranslation()
  const { contact, preferences, requestCode, verify, update } = useNotificationEmail()
  const [code, setCode] = useState('')
  const [now, setNow] = useState(Date.now())
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 1000); return () => window.clearInterval(timer) }, [])
  const value = contact.data
  const retryAt = value?.resendAvailableAt ? Date.parse(value.resendAvailableAt) : 0
  const remaining = Math.max(0, Math.ceil((retryAt - now) / 1000))
  const busy = requestCode.isPending || verify.isPending || update.isPending
  const error = requestCode.error || verify.error || update.error
  const canVerify = !!value?.available || value?.reason === 'UNVERIFIED'
  const canEnable = !!value?.available && !!value.verified
  const canEdit = !!value && !!preferences.data && !busy
  function changeMode(category: EmailCategory, mode: EmailMode) {
    update.mutate([{ category, mode }])
  }
  return <Card>
    <CardHeader><CardTitle>{t('notificationEmail.title')}</CardTitle><p className="text-sm text-muted-foreground">{t('notificationEmail.description')}</p></CardHeader>
    <CardContent className="space-y-5">
      {contact.isPending || preferences.isPending ? <p role="status">{t('notificationEmail.loading')}</p> : null}
      {contact.isError || preferences.isError ? <div role="alert"><p>{t('notificationEmail.loadFailed')}</p><Button size="sm" variant="outline" onClick={() => { void contact.refetch(); void preferences.refetch() }}>{t('notificationEmail.refresh')}</Button></div> : null}
      {value ? <div className="space-y-3 rounded-lg border p-4">
        <div className="flex flex-wrap items-center justify-between gap-2"><span className="break-all text-sm font-medium">{value.email || t('notificationEmail.noEmail')}</span><span className="text-sm">{t(value.verified ? 'notificationEmail.verified' : 'notificationEmail.unverified')}</span></div>
        {!canVerify ? <p role="status" className="text-sm text-muted-foreground">{t('notificationEmail.unavailable')}</p> : null}
        {!value.email ? <Link to="/settings/security" className="text-sm underline">{t('notificationEmail.manageEmail')}</Link> : null}
        {!value.verified && value.email ? <>
          <p className="text-sm text-muted-foreground">{t('notificationEmail.verifyHelp')}</p>
          <form className="flex flex-wrap items-end gap-2" onSubmit={(event) => { event.preventDefault(); if (canVerify && !busy && /^\d{6}$/.test(code)) verify.mutate(code, { onSuccess: () => setCode('') }) }}><div className="space-y-1"><Label htmlFor="notification-email-code">{t('notificationEmail.code')}</Label><Input id="notification-email-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} pattern="[0-9]{6}" className="w-40" value={code} onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))} disabled={busy || !canVerify} /></div>
          <Button type="button" size="sm" disabled={!canVerify || busy || remaining > 0} variant="outline" onClick={() => requestCode.mutate(i18n.language.startsWith('zh') ? 'zh' : 'en')}>{remaining ? t('notificationEmail.resendIn', { seconds: remaining }) : t('notificationEmail.sendCode')}</Button>
          <Button type="submit" size="sm" disabled={!canVerify || busy || !/^\d{6}$/.test(code)}>{t('notificationEmail.verify')}</Button></form>
        </> : null}
        {requestCode.isSuccess && !value.verified ? <p role="status" className="text-sm">{t('notificationEmail.codeSent')}</p> : null}
      </div> : null}
      <div className="divide-y">{EMAIL_CATEGORIES.map((category) => <div key={category} className="flex flex-wrap items-center justify-between gap-3 py-3">
        <div className="min-w-0 flex-1"><Label htmlFor={`email-mode-${category}`}>{t(`notification.preferences.${category.toLowerCase()}`)}</Label><p className="text-xs text-muted-foreground">{t(category === 'PUBLISH' ? 'notificationEmail.publishHelp' : 'notificationEmail.otherHelp')}</p></div>
        <select id={`email-mode-${category}`} className="h-9 rounded-md border bg-background px-3 text-sm" disabled={!canEdit} value={preferences.data?.find((item) => item.category === category)?.mode ?? 'OFF'} onChange={(event) => { const mode = event.target.value; if (mode === 'OFF' || mode === 'IMMEDIATE' || mode === 'DIGEST') changeMode(category, mode) }}>{(['OFF', 'IMMEDIATE', 'DIGEST'] as const).map((mode) => <option key={mode} value={mode} disabled={mode !== 'OFF' && !canEnable}>{t(`notificationEmail.mode.${mode}`)}</option>)}</select>
      </div>)}</div>
      {error ? <p role="alert" className="text-sm text-destructive">{t('notificationEmail.actionFailed')} {error instanceof Error ? t(error.message) : ''}</p> : null}
      {update.isSuccess && !update.isPending ? <p role="status" className="text-sm">{t('notificationEmail.saved')}</p> : null}
      <p className="text-xs text-muted-foreground">{t('notificationEmail.inFlight')}</p>
    </CardContent>
  </Card>
}
