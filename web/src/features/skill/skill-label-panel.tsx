import { useTranslation } from 'react-i18next'
import { Tag } from 'lucide-react'
import type { LabelDefinition, LabelItem, LabelTranslation } from '@/api/types'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { toast } from '@/shared/lib/toast'
import { cn } from '@/shared/lib/utils'
import {
  useAdminLabelDefinitions,
  useAttachSkillLabel,
  useAttachSuiteLabel,
  useDetachSkillLabel,
  useDetachSuiteLabel,
  useSkillLabels,
  useSuiteLabels,
  useVisibleLabels,
} from '@/shared/hooks/use-label-queries'

type SkillLabelPanelProps = {
  namespace: string
  slug: string
  initialLabels: LabelItem[]
  canManage: boolean
  isSuperAdmin: boolean
}

type MutationCallbacks = {
  onSuccess: () => void
  onError: (error: unknown) => void
}

type ResourceLabelPanelProps = SkillLabelPanelProps & {
  currentLabels?: LabelItem[]
  attachLabel: (labelSlug: string, callbacks: MutationCallbacks) => void
  detachLabel: (labelSlug: string, callbacks: MutationCallbacks) => void
  attachPending: boolean
  detachPending: boolean
  translationPrefix: 'skillDetail' | 'suite'
}

function canManageLabelType(type: string, isSuperAdmin: boolean) {
  return isSuperAdmin || type !== 'PRIVILEGED'
}

function resolveDisplayName(translations: LabelTranslation[], locale: string, fallbackSlug: string) {
  const normalizedLocale = locale.toLowerCase()
  const exact = translations.find((item) => item.locale.toLowerCase() === normalizedLocale)
  if (exact?.displayName) {
    return exact.displayName
  }

  const language = normalizedLocale.split('-')[0]
  const languageMatch = translations.find((item) => item.locale.toLowerCase().split('-')[0] === language)
  if (languageMatch?.displayName) {
    return languageMatch.displayName
  }

  return translations[0]?.displayName || fallbackSlug
}

function toCandidateLabel(definition: LabelDefinition, locale: string): LabelItem & { sortOrder: number } {
  return {
    slug: definition.slug,
    type: definition.type,
    displayName: resolveDisplayName(definition.translations, locale, definition.slug),
    sortOrder: definition.sortOrder,
  }
}

function sortByPresentation(left: { displayName: string; slug: string; sortOrder?: number }, right: { displayName: string; slug: string; sortOrder?: number }) {
  const leftOrder = left.sortOrder ?? Number.MAX_SAFE_INTEGER
  const rightOrder = right.sortOrder ?? Number.MAX_SAFE_INTEGER
  if (leftOrder !== rightOrder) {
    return leftOrder - rightOrder
  }
  return left.displayName.localeCompare(right.displayName, undefined, { sensitivity: 'base' })
    || left.slug.localeCompare(right.slug, undefined, { sensitivity: 'base' })
}

function ResourceLabelPanel({
  initialLabels,
  canManage,
  isSuperAdmin,
  currentLabels: queriedLabels,
  attachLabel,
  detachLabel,
  attachPending,
  detachPending,
  translationPrefix,
}: ResourceLabelPanelProps) {
  const { t, i18n } = useTranslation()
  const locale = i18n.resolvedLanguage || i18n.language || 'en'
  const { data: visibleLabels, isLoading: visibleLabelsLoading } = useVisibleLabels(canManage && !isSuperAdmin)
  const { data: adminDefinitions, isLoading: adminDefinitionsLoading } = useAdminLabelDefinitions(canManage && isSuperAdmin)

  if (!canManage) {
    return null
  }

  const currentLabels = (queriedLabels ?? initialLabels)
    .slice()
    .sort(sortByPresentation)
  const currentLabelSlugs = new Set(currentLabels.map((label) => label.slug))
  const candidateLabels = isSuperAdmin
    ? (adminDefinitions ?? []).map((definition) => toCandidateLabel(definition, locale))
    : (visibleLabels ?? []).map((label, index) => ({ ...label, sortOrder: index }))
  const availableLabels = candidateLabels
    .filter((label) => !currentLabelSlugs.has(label.slug))
    .filter((label) => canManageLabelType(label.type, isSuperAdmin))
    .sort(sortByPresentation)
  const isCatalogLoading = isSuperAdmin ? adminDefinitionsLoading : visibleLabelsLoading
  const isMutating = attachPending || detachPending

  const handleAttach = (labelSlug: string) => {
    attachLabel(labelSlug, {
      onSuccess: () => {
        toast.success(t(`${translationPrefix}.labelAttachSuccessTitle`), t(`${translationPrefix}.labelAttachSuccessDescription`))
      },
      onError: (error) => {
        toast.error(
          t(`${translationPrefix}.labelAttachErrorTitle`),
          error instanceof Error ? error.message : t(`${translationPrefix}.labelActionFallbackError`),
        )
      },
    })
  }

  const handleDetach = (label: LabelItem) => {
    detachLabel(label.slug, {
      onSuccess: () => {
        toast.success(t(`${translationPrefix}.labelDetachSuccessTitle`), t(`${translationPrefix}.labelDetachSuccessDescription`))
      },
      onError: (error) => {
        toast.error(
          t(`${translationPrefix}.labelDetachErrorTitle`),
          error instanceof Error ? error.message : t(`${translationPrefix}.labelActionFallbackError`),
        )
      },
    })
  }

  return (
    <Card className="p-5 space-y-4">
      <div className="space-y-1">
        <div className="flex items-center gap-2">
          <Tag className="w-4 h-4 text-muted-foreground" />
          <span className="text-sm font-semibold font-heading text-foreground">{t(`${translationPrefix}.labelsSectionTitle`)}</span>
        </div>
        <p className="text-sm text-muted-foreground">
          {isSuperAdmin ? t(`${translationPrefix}.labelsSectionDescriptionSuperAdmin`) : t(`${translationPrefix}.labelsSectionDescription`)}
        </p>
      </div>

      <div className="space-y-3">
        <div className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t(`${translationPrefix}.currentLabelsTitle`)}</div>
        {currentLabels.length > 0 ? (
          <div className="space-y-2">
            {currentLabels.map((label) => {
              const removable = canManageLabelType(label.type, isSuperAdmin)
              return (
                <div
                  key={label.slug}
                  className="flex items-center justify-between gap-3 rounded-xl border border-border/60 bg-secondary/20 px-3 py-2"
                >
                  <div className="min-w-0">
                    <div
                      className={cn(
                        'inline-flex items-center rounded-full border px-2.5 py-1 text-[11px] font-medium',
                        label.type === 'PRIVILEGED'
                          ? 'border-amber-500/40 bg-amber-100 text-amber-900 dark:bg-amber-950/60 dark:text-amber-300'
                          : 'border-border bg-secondary text-secondary-foreground',
                      )}
                    >
                      {label.displayName}
                    </div>
                    <div className="mt-1 text-xs text-muted-foreground">{label.slug}</div>
                  </div>
                  {removable ? (
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => handleDetach(label)}
                      disabled={isMutating}
                    >
                      {detachPending ? t(`${translationPrefix}.processing`) : t(`${translationPrefix}.removeLabel`)}
                    </Button>
                  ) : (
                    <span className="text-xs text-muted-foreground">{t(`${translationPrefix}.labelRestrictedHint`)}</span>
                  )}
                </div>
              )
            })}
          </div>
        ) : (
          <div className="rounded-xl border border-dashed border-border/70 px-3 py-4 text-sm text-muted-foreground">
            {t(`${translationPrefix}.noLabelsAssigned`)}
          </div>
        )}
      </div>

      <div className="space-y-3">
        <div className="text-xs uppercase tracking-[0.18em] text-muted-foreground">{t(`${translationPrefix}.availableLabelsTitle`)}</div>
        {isCatalogLoading ? (
          <div className="rounded-xl border border-dashed border-border/70 px-3 py-4 text-sm text-muted-foreground">
            {t(`${translationPrefix}.loadingAvailableLabels`)}
          </div>
        ) : availableLabels.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            {availableLabels.map((label) => (
              <Button
                key={label.slug}
                variant="outline"
                size="sm"
                className="rounded-full"
                onClick={() => handleAttach(label.slug)}
                disabled={isMutating}
              >
                {attachPending ? t(`${translationPrefix}.processing`) : t(`${translationPrefix}.addLabel`, { label: label.displayName })}
              </Button>
            ))}
          </div>
        ) : (
          <div className="rounded-xl border border-dashed border-border/70 px-3 py-4 text-sm text-muted-foreground">
            {t(`${translationPrefix}.noAvailableLabels`)}
          </div>
        )}
      </div>
    </Card>
  )
}

export function SkillLabelPanel(props: SkillLabelPanelProps) {
  const { data: labels } = useSkillLabels(props.namespace, props.slug, props.canManage)
  const attachMutation = useAttachSkillLabel()
  const detachMutation = useDetachSkillLabel()
  return (
    <ResourceLabelPanel
      {...props}
      currentLabels={labels}
      attachLabel={(labelSlug, callbacks) => attachMutation.mutate(
        { namespace: props.namespace, slug: props.slug, labelSlug }, callbacks,
      )}
      detachLabel={(labelSlug, callbacks) => detachMutation.mutate(
        { namespace: props.namespace, slug: props.slug, labelSlug }, callbacks,
      )}
      attachPending={attachMutation.isPending}
      detachPending={detachMutation.isPending}
      translationPrefix="skillDetail"
    />
  )
}

export function SuiteLabelPanel(props: SkillLabelPanelProps) {
  const { data: labels } = useSuiteLabels(props.namespace, props.slug, props.canManage)
  const attachMutation = useAttachSuiteLabel()
  const detachMutation = useDetachSuiteLabel()
  return (
    <ResourceLabelPanel
      {...props}
      currentLabels={labels}
      attachLabel={(labelSlug, callbacks) => attachMutation.mutate(
        { namespace: props.namespace, slug: props.slug, labelSlug }, callbacks,
      )}
      detachLabel={(labelSlug, callbacks) => detachMutation.mutate(
        { namespace: props.namespace, slug: props.slug, labelSlug }, callbacks,
      )}
      attachPending={attachMutation.isPending}
      detachPending={detachMutation.isPending}
      translationPrefix="suite"
    />
  )
}
