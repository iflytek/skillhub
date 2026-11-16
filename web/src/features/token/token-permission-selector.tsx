import { Check } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { cn } from '@/shared/lib/utils'
import {
  getTokenPreset,
  TOKEN_CAPABILITIES,
  TOKEN_PRESETS,
} from './token-capabilities'
import { TokenPermissionDetails } from './token-permission-details'

export function TokenPermissionSelector({
  scopes,
  onChange,
  disabled,
}: {
  scopes: readonly string[]
  onChange: (scopes: string[]) => void
  disabled?: boolean
}) {
  const { t } = useTranslation()
  const preset = getTokenPreset(scopes) ?? 'custom'
  const sensitive =
    scopes.includes('skill:delete') || scopes.includes('token:manage')
  return (
    <section
      aria-labelledby="token-permissions-title"
      className="min-w-0 space-y-3"
    >
      <div className="flex flex-wrap items-center justify-between gap-x-3 gap-y-1">
        <h3 id="token-permissions-title" className="text-sm font-medium">
          {t('token.permissionsTitle')}
        </h3>
        <div className="flex items-center gap-3">
          <span
            className="w-32 text-right text-xs text-muted-foreground"
            role="status"
          >
            {preset === 'custom' && `${t('createToken.presets.custom')} · `}
            {t('token.selectedCapabilityCount', { count: scopes.length })}
          </span>
          <TokenPermissionDetails scopes={scopes} showAll disabled={disabled} />
        </div>
      </div>
      <div
        className="flex flex-wrap gap-2"
        role="group"
        aria-label={t('token.permissionShortcuts')}
      >
        {(Object.keys(TOKEN_PRESETS) as (keyof typeof TOKEN_PRESETS)[]).map(
          (value) => (
            <Button
              key={value}
              type="button"
              size="sm"
              variant="outline"
              disabled={disabled}
              aria-pressed={preset === value}
              className={cn(
                'flex-1 px-2 text-xs sm:flex-none sm:px-4',
                preset === value &&
                  'border-info/35 bg-info/5 text-info hover:text-info',
              )}
              onClick={() => onChange([...TOKEN_PRESETS[value]])}
            >
              {t(`createToken.presets.${value}`)}
            </Button>
          ),
        )}
      </div>
      <div
        className="grid grid-cols-2 gap-2 sm:grid-cols-3"
        role="group"
        aria-label={t('token.permissionsTitle')}
      >
        {TOKEN_CAPABILITIES.map(({ scope, label }) => {
          const selected = scopes.includes(scope)
          const dangerous = scope === 'skill:delete' || scope === 'token:manage'
          return (
            <Button
              key={scope}
              type="button"
              variant="outline"
              disabled={disabled}
              aria-label={`${t(`token.capabilityLabels.${label}`)} (${scope})`}
              aria-pressed={selected}
              className={cn(
                'h-8 min-w-0 justify-between gap-1 px-2 text-xs',
                selected &&
                  (dangerous
                    ? 'border-destructive/30 bg-destructive/5 text-destructive hover:text-destructive'
                    : 'border-info/35 bg-info/5 text-info hover:text-info'),
              )}
              onClick={() =>
                onChange(
                  TOKEN_CAPABILITIES.filter((item) =>
                    item.scope === scope
                      ? !selected
                      : scopes.includes(item.scope),
                  ).map((item) => item.scope),
                )
              }
            >
              <span className="truncate">
                {t(`token.capabilityLabels.${label}`)}
              </span>
              <Check
                aria-hidden="true"
                className={cn('h-3.5 w-3.5 shrink-0', !selected && 'invisible')}
              />
            </Button>
          )
        })}
      </div>
      <p
        className={cn(
          'flex h-14 items-center sm:h-10 rounded-md px-3 py-2 text-xs leading-5',
          sensitive
            ? 'bg-warning/10 text-warning-foreground dark:text-warning'
            : 'bg-muted/40 text-muted-foreground',
        )}
        aria-live="polite"
      >
        {t(
          scopes.length === 0
            ? 'createToken.selectCapability'
            : sensitive
              ? 'token.sensitiveCapabilityHint'
              : 'token.permissionSelectionHint',
        )}
      </p>
    </section>
  )
}
