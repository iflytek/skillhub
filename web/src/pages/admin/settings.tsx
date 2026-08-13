import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import type { PersonalNamespaceSettingsInput } from '@/api/types'
import {
  usePersonalNamespaceSettings,
  useUpdatePersonalNamespaceSettings,
} from '@/features/admin/use-personal-namespace-settings'

/**
 * Sample account used for the live template preview.
 */
const PREVIEW_OWNER: Record<string, string> = {
  username: 'Li.Wei',
  email_prefix: 'li.wei',
  user_id: 'usr_4f9c2a1b',
}

export function renderTemplate(template: string): string {
  return template.replace(/\$\{([a-z_]+)}/g, (match, name: string) => PREVIEW_OWNER[name] ?? match)
}

/**
 * Mirrors the server's slug rules so operators can see the effect of a template — in particular
 * that underscores and dots become hyphens — before saving it.
 */
export function previewSlug(template: string): string {
  return renderTemplate(template)
    .trim()
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, '-')
    .replace(/^-+/, '')
    .replace(/-+$/, '')
    .replace(/-{2,}/g, '-')
}

export function AdminSettingsPage() {
  const { t } = useTranslation()
  const { data: settings, isLoading } = usePersonalNamespaceSettings()
  const updateMutation = useUpdatePersonalNamespaceSettings()

  const [form, setForm] = useState<PersonalNamespaceSettingsInput>({
    enabled: false,
    slugTemplate: '${username}',
    displayNameTemplate: '${username}',
  })

  useEffect(() => {
    if (settings) {
      setForm({
        enabled: settings.enabled,
        slugTemplate: settings.slugTemplate,
        displayNameTemplate: settings.displayNameTemplate,
      })
    }
  }, [settings])

  const slugPreview = previewSlug(form.slugTemplate)
  const displayNamePreview = renderTemplate(form.displayNameTemplate).trim()
  const placeholders = settings?.supportedPlaceholders ?? Object.keys(PREVIEW_OWNER)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()

    if (!form.slugTemplate.trim() || !form.displayNameTemplate.trim()) {
      toast.error(t('adminSettings.validationTitle'), t('adminSettings.validationTemplateRequired'))
      return
    }

    try {
      await updateMutation.mutateAsync(form)
      toast.success(t('adminSettings.saveSuccessTitle'), t('adminSettings.saveSuccessDescription'))
    } catch (error) {
      toast.error(
        t('adminSettings.saveErrorTitle'),
        error instanceof Error ? error.message : t('adminSettings.fallbackErrorDescription'),
      )
    }
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div>
        <h1 className="mb-2 text-4xl font-bold font-heading">{t('adminSettings.title')}</h1>
        <p className="text-lg text-muted-foreground">{t('adminSettings.subtitle')}</p>
      </div>

      <Card className="p-6">
        <div className="mb-6">
          <h2 className="text-xl font-semibold font-heading">{t('adminSettings.personalNamespaceTitle')}</h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {t('adminSettings.personalNamespaceDescription')}
          </p>
        </div>

        {isLoading ? (
          <div className="text-sm text-muted-foreground">{t('adminSettings.loading')}</div>
        ) : (
          <form className="space-y-6" onSubmit={handleSubmit}>
            <div className="grid gap-2 md:max-w-xs">
              <Label htmlFor="personal-namespace-enabled">{t('adminSettings.enabledLabel')}</Label>
              <Select
                value={form.enabled ? 'enabled' : 'disabled'}
                onValueChange={(value) =>
                  setForm((current) => ({ ...current, enabled: value === 'enabled' }))
                }
              >
                <SelectTrigger id="personal-namespace-enabled">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="enabled">{t('adminSettings.enabledOn')}</SelectItem>
                  <SelectItem value="disabled">{t('adminSettings.enabledOff')}</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="personal-namespace-slug-template">{t('adminSettings.slugTemplateLabel')}</Label>
              <Input
                id="personal-namespace-slug-template"
                value={form.slugTemplate}
                disabled={!form.enabled}
                onChange={(event) =>
                  setForm((current) => ({ ...current, slugTemplate: event.target.value }))
                }
              />
              <p className="text-xs text-muted-foreground">
                {t('adminSettings.placeholderHint', { placeholders: placeholders.map((name) => `\${${name}}`).join(', ') })}
              </p>
              <p className="text-xs text-muted-foreground">
                {t('adminSettings.slugPreview', { slug: slugPreview || '—' })}
              </p>
              <p className="text-xs text-muted-foreground">{t('adminSettings.slugRulesHint')}</p>
            </div>

            <div className="grid gap-2">
              <Label htmlFor="personal-namespace-display-template">
                {t('adminSettings.displayNameTemplateLabel')}
              </Label>
              <Input
                id="personal-namespace-display-template"
                value={form.displayNameTemplate}
                disabled={!form.enabled}
                onChange={(event) =>
                  setForm((current) => ({ ...current, displayNameTemplate: event.target.value }))
                }
              />
              <p className="text-xs text-muted-foreground">
                {t('adminSettings.displayNamePreview', { displayName: displayNamePreview || '—' })}
              </p>
            </div>

            <div className="flex justify-end">
              <Button type="submit" disabled={updateMutation.isPending}>
                {updateMutation.isPending ? t('adminSettings.saving') : t('adminSettings.saveAction')}
              </Button>
            </div>
          </form>
        )}
      </Card>
    </div>
  )
}
