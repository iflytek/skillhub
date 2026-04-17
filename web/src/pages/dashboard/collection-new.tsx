import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { useCreateCollection } from '@/shared/hooks/use-collection-queries'
import { mapCollectionMutationError } from '@/shared/lib/skill-collection-form-errors'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

/**
 * Create collection form (owner-only metadata; contributors are out of scope here).
 */
export function CollectionNewPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const createMutation = useCreateCollection()

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [slug, setSlug] = useState('')
  const [visibility, setVisibility] = useState<'PUBLIC' | 'PRIVATE'>('PRIVATE')

  const [fieldErrors, setFieldErrors] = useState<Partial<Record<'title' | 'description' | 'visibility' | 'slug', string>>>({})
  const [formError, setFormError] = useState<string | undefined>()

  const clearFieldError = (field: keyof typeof fieldErrors) => {
    setFieldErrors((prev) => {
      const next = { ...prev }
      delete next[field]
      return next
    })
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setFormError(undefined)
    setFieldErrors({})

    try {
      const created = await createMutation.mutateAsync({
        title: title.trim(),
        description: description.trim() || undefined,
        visibility,
        slug: slug.trim(),
      })
      if (created.id == null) {
        setFormError(t('collections.createMissingId'))
        return
      }
      navigate({
        to: '/dashboard/collections/$collectionId',
        params: { collectionId: String(created.id) },
      })
    } catch (error) {
      const mapped = mapCollectionMutationError(error)
      setFieldErrors(mapped.fieldErrors)
      setFormError(mapped.formError)
    }
  }

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <DashboardPageHeader
        title={t('collections.createTitle')}
        subtitle={t('collections.createSubtitle')}
        actions={(
          <Link to="/dashboard/collections" className={buttonVariants({ variant: 'outline' })} data-testid="collection-new-back-to-list">
            {t('collections.backToList')}
          </Link>
        )}
      />

      <form onSubmit={handleSubmit} className="mx-auto max-w-xl space-y-6" data-testid="collection-new-form">
        {formError ? <p className="text-sm text-destructive">{formError}</p> : null}

        <div className="space-y-2">
          <Label htmlFor="collection-title">{t('collections.fieldTitle')}</Label>
          <Input
            id="collection-title"
            data-testid="collection-new-title-input"
            value={title}
            onChange={(e) => {
              setTitle(e.target.value)
              clearFieldError('title')
            }}
            autoComplete="off"
            aria-invalid={fieldErrors.title ? 'true' : 'false'}
          />
          {fieldErrors.title ? <p className="text-sm text-destructive">{fieldErrors.title}</p> : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="collection-description">{t('collections.fieldDescription')}</Label>
          <Textarea
            id="collection-description"
            data-testid="collection-new-description-input"
            value={description}
            onChange={(e) => {
              setDescription(e.target.value)
              clearFieldError('description')
            }}
            rows={4}
            aria-invalid={fieldErrors.description ? 'true' : 'false'}
          />
          {fieldErrors.description ? <p className="text-sm text-destructive">{fieldErrors.description}</p> : null}
        </div>

        <div className="space-y-2">
          <Label htmlFor="collection-slug">{t('collections.fieldSlug')}</Label>
          <Input
            id="collection-slug"
            data-testid="collection-new-slug-input"
            value={slug}
            onChange={(e) => {
              setSlug(e.target.value)
              clearFieldError('slug')
            }}
            autoComplete="off"
            aria-invalid={fieldErrors.slug ? 'true' : 'false'}
          />
          {fieldErrors.slug ? <p className="text-sm text-destructive">{fieldErrors.slug}</p> : null}
        </div>

        <div className="space-y-2">
          <Label>{t('collections.fieldVisibility')}</Label>
          <Select
            value={visibility}
            onValueChange={(value) => {
              setVisibility(value as 'PUBLIC' | 'PRIVATE')
              clearFieldError('visibility')
            }}
          >
            <SelectTrigger aria-invalid={fieldErrors.visibility ? 'true' : 'false'} data-testid="collection-new-visibility-trigger">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="PUBLIC" data-testid="collection-new-visibility-public">{t('collections.visibilityPublic')}</SelectItem>
              <SelectItem value="PRIVATE" data-testid="collection-new-visibility-private">{t('collections.visibilityPrivate')}</SelectItem>
            </SelectContent>
          </Select>
          {fieldErrors.visibility ? <p className="text-sm text-destructive">{fieldErrors.visibility}</p> : null}
        </div>

        <div className="flex gap-3">
          <Button type="submit" disabled={createMutation.isPending} data-testid="collection-new-submit">
            {createMutation.isPending ? t('collections.saving') : t('collections.submitCreate')}
          </Button>
          <Link to="/dashboard/collections" className={buttonVariants({ variant: 'ghost' })} data-testid="collection-new-cancel">
            {t('collections.cancel')}
          </Link>
        </div>
      </form>
    </div>
  )
}
