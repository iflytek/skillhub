import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatLocalDateTime } from '@/shared/lib/date-time'
import { toast } from '@/shared/lib/toast'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import type { AdminLabelInput, LabelDefinition, LabelTranslation } from '@/api/types'
import {
  useAdminLabelDefinitions,
  useCreateAdminLabel,
  useDeleteAdminLabel,
  useUpdateAdminLabel,
  useUpdateAdminLabelSortOrder,
} from '@/features/admin/use-admin-labels'

type LabelFormState = AdminLabelInput

const EMPTY_TRANSLATION: LabelTranslation = { locale: '', displayName: '' }
const LABEL_SLUG_PATTERN = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/

function toFormState(definition?: LabelDefinition): LabelFormState {
  if (!definition) {
    return {
      slug: '',
      type: 'RECOMMENDED',
      visibleInFilter: true,
      sortOrder: 0,
      translations: [{ ...EMPTY_TRANSLATION }],
    }
  }

  return {
    slug: definition.slug,
    type: definition.type === 'PRIVILEGED' ? 'PRIVILEGED' : 'RECOMMENDED',
    visibleInFilter: definition.visibleInFilter,
    sortOrder: definition.sortOrder,
    translations: definition.translations.length > 0 ? definition.translations : [{ ...EMPTY_TRANSLATION }],
  }
}

export function normalizeLabelFormState(form: LabelFormState): LabelFormState {
  return {
    ...form,
    slug: form.slug.trim().toLowerCase(),
    sortOrder: Number.isFinite(form.sortOrder) ? form.sortOrder : 0,
    translations: form.translations
      .map((translation) => ({
        locale: translation.locale.trim().replace(/_/g, '-').toLowerCase(),
        displayName: translation.displayName.trim(),
      }))
      .filter((translation) => translation.locale && translation.displayName),
  }
}

export function validateLabelFormState(form: LabelFormState): {
  titleKey: string
  descriptionKey: string
} | null {
  if (!form.slug) {
    return {
      titleKey: 'adminLabels.validationSlugTitle',
      descriptionKey: 'adminLabels.validationSlugDescription',
    }
  }
  if (!LABEL_SLUG_PATTERN.test(form.slug) || form.slug.includes('--')) {
    return {
      titleKey: 'adminLabels.validationSlugTitle',
      descriptionKey: 'adminLabels.validationSlugPatternDescription',
    }
  }
  if (form.translations.length === 0) {
    return {
      titleKey: 'adminLabels.validationTranslationsTitle',
      descriptionKey: 'adminLabels.validationTranslationsDescription',
    }
  }

  const uniqueLocales = new Set(form.translations.map((translation) => translation.locale))
  if (uniqueLocales.size !== form.translations.length) {
    return {
      titleKey: 'adminLabels.validationTranslationsTitle',
      descriptionKey: 'adminLabels.validationDuplicateLocaleDescription',
    }
  }

  return null
}

function moveItem(definitions: LabelDefinition[], fromIndex: number, toIndex: number) {
  if (toIndex < 0 || toIndex >= definitions.length) {
    return definitions
  }
  const next = definitions.slice()
  const [item] = next.splice(fromIndex, 1)
  next.splice(toIndex, 0, item)
  return next.map((definition, index) => ({ ...definition, sortOrder: index }))
}

export function AdminLabelsPage() {
  const { t, i18n } = useTranslation()
  const { data: definitions, isLoading } = useAdminLabelDefinitions()
  const createMutation = useCreateAdminLabel()
  const updateMutation = useUpdateAdminLabel()
  const deleteMutation = useDeleteAdminLabel()
  const updateSortOrderMutation = useUpdateAdminLabelSortOrder()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [editingSlug, setEditingSlug] = useState<string | null>(null)
  const [pendingDelete, setPendingDelete] = useState<LabelDefinition | null>(null)
  const [form, setForm] = useState<LabelFormState>(toFormState())

  const sortedDefinitions: LabelDefinition[] = [...(definitions ?? [])].sort((a: LabelDefinition, b: LabelDefinition) => {
    if (a.sortOrder !== b.sortOrder) {
      return a.sortOrder - b.sortOrder
    }
    return a.slug.localeCompare(b.slug)
  })

  useEffect(() => {
    if (!dialogOpen) {
      setForm(toFormState())
      setEditingSlug(null)
    }
  }, [dialogOpen])

  const openCreateDialog = () => {
    setEditingSlug(null)
    setForm(toFormState())
    setDialogOpen(true)
  }

  const openEditDialog = (definition: LabelDefinition) => {
    setEditingSlug(definition.slug)
    setForm(toFormState(definition))
    setDialogOpen(true)
  }

  const handleTranslationChange = (index: number, field: keyof LabelTranslation, value: string) => {
    setForm((current) => ({
      ...current,
      translations: current.translations.map((translation, translationIndex) =>
        translationIndex === index ? { ...translation, [field]: value } : translation,
      ),
    }))
  }

  const addTranslation = () => {
    setForm((current) => ({
      ...current,
      translations: [...current.translations, { ...EMPTY_TRANSLATION }],
    }))
  }

  const removeTranslation = (index: number) => {
    setForm((current) => ({
      ...current,
      translations: current.translations.length === 1
        ? [{ ...EMPTY_TRANSLATION }]
        : current.translations.filter((_, translationIndex) => translationIndex !== index),
    }))
  }

  const handleSubmit = async () => {
    const normalized = normalizeLabelFormState(form)
    const validationError = validateLabelFormState(normalized)
    if (validationError) {
      toast.error(t(validationError.titleKey), t(validationError.descriptionKey))
      return
    }

    try {
      if (editingSlug) {
        await updateMutation.mutateAsync({
          slug: editingSlug,
          request: {
            type: normalized.type,
            visibleInFilter: normalized.visibleInFilter,
            sortOrder: normalized.sortOrder,
            translations: normalized.translations,
          },
        })
        toast.success(t('adminLabels.updateSuccessTitle'), t('adminLabels.updateSuccessDescription'))
      } else {
        await createMutation.mutateAsync(normalized)
        toast.success(t('adminLabels.createSuccessTitle'), t('adminLabels.createSuccessDescription'))
      }
      setDialogOpen(false)
    } catch (error) {
      toast.error(
        editingSlug ? t('adminLabels.updateErrorTitle') : t('adminLabels.createErrorTitle'),
        error instanceof Error ? error.message : t('adminLabels.fallbackErrorDescription'),
      )
    }
  }

  const handleDelete = async () => {
    if (!pendingDelete) {
      return
    }
    try {
      await deleteMutation.mutateAsync(pendingDelete.slug)
      toast.success(t('adminLabels.deleteSuccessTitle'), t('adminLabels.deleteSuccessDescription'))
      setDeleteDialogOpen(false)
      setPendingDelete(null)
    } catch (error) {
      toast.error(t('adminLabels.deleteErrorTitle'), error instanceof Error ? error.message : t('adminLabels.fallbackErrorDescription'))
    }
  }

  const handleMove = async (fromIndex: number, direction: -1 | 1) => {
    const nextDefinitions = moveItem(sortedDefinitions, fromIndex, fromIndex + direction)
    const payload = nextDefinitions.map((definition, index) => ({ slug: definition.slug, sortOrder: index }))

    try {
      await updateSortOrderMutation.mutateAsync(payload)
      toast.success(t('adminLabels.sortSuccessTitle'), t('adminLabels.sortSuccessDescription'))
    } catch (error) {
      toast.error(t('adminLabels.sortErrorTitle'), error instanceof Error ? error.message : t('adminLabels.fallbackErrorDescription'))
    }
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <h1 className="mb-2 text-4xl font-bold font-heading">{t('adminLabels.title')}</h1>
          <p className="text-lg text-muted-foreground">{t('adminLabels.subtitle')}</p>
        </div>
        <Button type="button" onClick={openCreateDialog}>
          {t('adminLabels.createAction')}
        </Button>
      </div>

      <Card className="p-5">
        <div className="grid gap-4 md:grid-cols-[1.4fr_1fr_1fr]">
          <div>
            <div className="text-sm font-semibold text-foreground">{t('adminLabels.summaryDefinitionsTitle')}</div>
            <div className="mt-1 text-3xl font-bold font-heading text-foreground">{sortedDefinitions.length}</div>
          </div>
          <div>
            <div className="text-sm font-semibold text-foreground">{t('adminLabels.summaryVisibleTitle')}</div>
            <div className="mt-1 text-3xl font-bold font-heading text-foreground">
              {sortedDefinitions.filter((definition) => definition.visibleInFilter).length}
            </div>
          </div>
          <div>
            <div className="text-sm font-semibold text-foreground">{t('adminLabels.summaryPrivilegedTitle')}</div>
            <div className="mt-1 text-3xl font-bold font-heading text-foreground">
              {sortedDefinitions.filter((definition) => definition.type === 'PRIVILEGED').length}
            </div>
          </div>
        </div>
      </Card>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, index) => (
            <div key={index} className="h-14 animate-shimmer rounded-lg" />
          ))}
        </div>
      ) : sortedDefinitions.length === 0 ? (
        <Card className="p-12 text-center">
          <p className="text-muted-foreground">{t('adminLabels.empty')}</p>
        </Card>
      ) : (
        <Card>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('adminLabels.colLabel')}</TableHead>
                <TableHead>{t('adminLabels.colType')}</TableHead>
                <TableHead>{t('adminLabels.colVisibility')}</TableHead>
                <TableHead>{t('adminLabels.colSortOrder')}</TableHead>
                <TableHead>{t('adminLabels.colTranslations')}</TableHead>
                <TableHead>{t('adminLabels.colCreatedAt')}</TableHead>
                <TableHead>{t('adminLabels.colActions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sortedDefinitions.map((definition: LabelDefinition, index: number) => (
                <TableRow key={definition.slug}>
                  <TableCell>
                    <div className="font-medium">{definition.slug}</div>
                    <div className="text-xs text-muted-foreground">
                      {definition.translations[0]?.displayName ?? definition.slug}
                    </div>
                  </TableCell>
                  <TableCell>{definition.type}</TableCell>
                  <TableCell>
                    {definition.visibleInFilter ? t('adminLabels.visibilityVisible') : t('adminLabels.visibilityHidden')}
                  </TableCell>
                  <TableCell>{definition.sortOrder}</TableCell>
                  <TableCell>{definition.translations.length}</TableCell>
                  <TableCell>{definition.createdAt ? formatLocalDateTime(definition.createdAt, i18n.language) : '-'}</TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-2">
                      <Button type="button" size="sm" variant="outline" onClick={() => handleMove(index, -1)} disabled={index === 0 || updateSortOrderMutation.isPending}>
                        {t('adminLabels.moveUp')}
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => handleMove(index, 1)}
                        disabled={index === sortedDefinitions.length - 1 || updateSortOrderMutation.isPending}
                      >
                        {t('adminLabels.moveDown')}
                      </Button>
                      <Button type="button" size="sm" variant="outline" onClick={() => openEditDialog(definition)}>
                        {t('adminLabels.editAction')}
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        onClick={() => {
                          setPendingDelete(definition)
                          setDeleteDialogOpen(true)
                        }}
                      >
                        {t('adminLabels.deleteAction')}
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      )}

      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="w-[min(calc(100vw-2rem),44rem)]">
          <DialogHeader>
            <DialogTitle>{editingSlug ? t('adminLabels.editDialogTitle') : t('adminLabels.createDialogTitle')}</DialogTitle>
            <DialogDescription>
              {editingSlug ? t('adminLabels.editDialogDescription') : t('adminLabels.createDialogDescription')}
            </DialogDescription>
          </DialogHeader>

          <div className="grid gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="label-slug">{t('adminLabels.formSlug')}</Label>
              <Input
                id="label-slug"
                value={form.slug}
                disabled={Boolean(editingSlug)}
                onChange={(event) => setForm((current) => ({ ...current, slug: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="label-type">{t('adminLabels.formType')}</Label>
              <Select
                value={form.type}
                onValueChange={(value) => setForm((current) => ({ ...current, type: value as LabelFormState['type'] }))}
              >
                <SelectTrigger id="label-type">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="RECOMMENDED">{t('adminLabels.typeRecommended')}</SelectItem>
                  <SelectItem value="PRIVILEGED">{t('adminLabels.typePrivileged')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="label-visibility">{t('adminLabels.formVisibility')}</Label>
              <Select
                value={form.visibleInFilter ? 'visible' : 'hidden'}
                onValueChange={(value) => setForm((current) => ({ ...current, visibleInFilter: value === 'visible' }))}
              >
                <SelectTrigger id="label-visibility">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="visible">{t('adminLabels.visibilityVisible')}</SelectItem>
                  <SelectItem value="hidden">{t('adminLabels.visibilityHidden')}</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="label-sort-order">{t('adminLabels.formSortOrder')}</Label>
              <Input
                id="label-sort-order"
                type="number"
                value={String(form.sortOrder)}
                onChange={(event) => setForm((current) => ({ ...current, sortOrder: Number(event.target.value) }))}
              />
            </div>
          </div>

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <div className="text-sm font-semibold text-foreground">{t('adminLabels.formTranslations')}</div>
                <div className="text-xs text-muted-foreground">{t('adminLabels.formTranslationsHint')}</div>
              </div>
              <Button type="button" variant="outline" size="sm" onClick={addTranslation}>
                {t('adminLabels.addTranslation')}
              </Button>
            </div>

            <div className="space-y-3">
              {form.translations.map((translation, index) => (
                <div key={`${editingSlug ?? 'new'}-${index}`} className="grid gap-3 rounded-xl border border-border/60 p-3 md:grid-cols-[140px_minmax(0,1fr)_96px]">
                  <Input
                    placeholder={t('adminLabels.translationLocalePlaceholder')}
                    value={translation.locale}
                    onChange={(event) => handleTranslationChange(index, 'locale', event.target.value)}
                  />
                  <Input
                    placeholder={t('adminLabels.translationDisplayNamePlaceholder')}
                    value={translation.displayName}
                    onChange={(event) => handleTranslationChange(index, 'displayName', event.target.value)}
                  />
                  <Button type="button" variant="outline" onClick={() => removeTranslation(index)}>
                    {t('adminLabels.removeTranslation')}
                  </Button>
                </div>
              ))}
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>
              {t('adminLabels.cancelAction')}
            </Button>
            <Button
              type="button"
              onClick={handleSubmit}
              disabled={createMutation.isPending || updateMutation.isPending}
            >
              {editingSlug ? t('adminLabels.saveAction') : t('adminLabels.createAction')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t('adminLabels.deleteDialogTitle')}</DialogTitle>
            <DialogDescription>
              {pendingDelete ? t('adminLabels.deleteDialogDescription', { slug: pendingDelete.slug }) : ''}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setDeleteDialogOpen(false)}>
              {t('adminLabels.cancelAction')}
            </Button>
            <Button type="button" onClick={handleDelete} disabled={deleteMutation.isPending}>
              {t('adminLabels.deleteAction')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
