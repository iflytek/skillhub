import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/shared/ui/card'

/**
 * Account merge is intentionally unavailable until the platform can prove independent control of
 * both accounts. Keep the settings route so existing links remain valid, but do not render any
 * legacy identifier, token, verification, or confirmation controls.
 */
export function AccountSettingsPage() {
  const { t } = useTranslation()

  return (
    <div className="mx-auto max-w-3xl">
      <Card className="glass-strong">
        <CardHeader>
          <CardTitle>{t('accounts.unavailableTitle')}</CardTitle>
          <CardDescription>{t('accounts.unavailableDescription')}</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            {t('accounts.unavailableOperatorAction')}
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
