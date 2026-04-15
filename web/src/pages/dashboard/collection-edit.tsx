import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import {
  useCollectionDetail,
  useSetCollectionVisibility,
  useUpdateCollection,
} from '@/shared/hooks/use-collection-queries'
import { mapCollectionMutationError } from '@/shared/lib/skill-collection-form-errors'
import { toast } from '@/shared/lib/toast'
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
 * Edit collection metadata and visibility (owner-only mutations; server enforces contributor denial).
 */
export function CollectionEditPage() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const { collectionId } = useParams({ from: '/dashboard/collections/$collectionId/edit' })
  const { data, isLoading, error } = useCollectionDetail(collectionId)
  const updateMutation = useUpdateCollection()
  const visibilityMutation = useSetCollectionVisibility()

  const [title, setTitle] = useState('')
  const [description, setDescription] = useState('')
  const [slug, setSlug] = useState('')
  const [visibility, setVisibility] = useState<'PUBLIC' | 'PRIVATE'>('PRIVATE')

  const [fieldErrors, setFieldErrors] = useState<Partial<Record<'title' | 'description' | 'visibility' | 'slug', string>>>({})
  const [formError, setFormError] = useState<string | undefined>()

  useEffect(() => {
    if (!data) {
      return
    }
    setTitle(data.title ?? '')
    setDescription(data.description ?? '')
    setSlug(data.slug ?? '')
    setVisibility(data.visibility === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE')
    setFieldErrors({})
    setFormError(undefined)
  }, [data])

  const isOwner = user?.userId != null && data?.ownerId === user.userId
  const canEdit = !!isOwner

  const clearFieldError = (field: keyof typeof fieldErrors) => {
    setFieldErrors((prev) => {
      const next = { ...prev }
      delete next[field]
      return next
    })
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    if (!canEdit || !collectionId) {
      return
    }
    setFormError(undefined)
    setFieldErrors({})

    try {
      await updateMutation.mutateAsync({
        id: collectionId,
        body: {
          title: title.trim(),
          description: description.trim() || undefined,
          slug: slug.trim(),
        },
      })
      if (data && visibility !== (data.visibility === 'PUBLIC' ? 'PUBLIC' : 'PRIVATE')) {
        await visibilityMutation.mutateAsync({ id: collectionId, visibility })
      }
      toast.success(t('collections.saveSuccess'))
    } catch (err) {
      const mapped = mapCollectionMutationError(err)
      setFieldErrors(mapped.fieldErrors)
      setFormError(mapped.formError)
      if (mapped.formError && Object.keys(mapped.fieldErrors).length === 0) {
        toast.error(mapped.formError)
      }
    }
  }

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <DashboardPageHeader
        title={t('collections.editTitle')}
        subtitle={t('collections.editSubtitle')}
        actions={(
          <Link to="/dashboard/collections" className={buttonVariants({ variant: 'outline' })}>
            {t('collections.backToList')}
          </Link>
        )}
      />

      {isLoading ? <p className="text-sm text-muted-foreground">{t('collections.loading')}</p> : null}

      {error ? <p className="text-sm text-destructive">{t('collections.loadError')}</p> : null}

      {data && !canEdit ? (
        <p className="max-w-xl text-sm text-muted-foreground">{t('collections.contributorReadOnly')}</p>
      ) : null}

      {data ? (
        <form onSubmit={handleSubmit} className="mx-auto max-w-xl space-y-6">
          {formError ? <p className="text-sm text-destructive">{formError}</p> : null}

          <div className="space-y-2">
            <Label htmlFor="edit-collection-title">{t('collections.fieldTitle')}</Label>
            <Input
              id="edit-collection-title"
              value={title}
              disabled={!canEdit}
              onChange={(e) => {
                setTitle(e.target.value)
                clearFieldError('title')
              }}
              aria-invalid={fieldErrors.title ? 'true' : 'false'}
            />
            {fieldErrors.title ? <p className="text-sm text-destructive">{fieldErrors.title}</p> : null}
          </div>

          <div className="space-y-2">
            <Label htmlFor="edit-collection-description">{t('collections.fieldDescription')}</Label>
            <Textarea
              id="edit-collection-description"
              value={description}
              disabled={!canEdit}
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
            <Label htmlFor="edit-collection-slug">{t('collections.fieldSlug')}</Label>
            <Input
              id="edit-collection-slug"
              value={slug}
              disabled={!canEdit}
              onChange={(e) => {
                setSlug(e.target.value)
                clearFieldError('slug')
              }}
              aria-invalid={fieldErrors.slug ? 'true' : 'false'}
            />
            {fieldErrors.slug ? <p className="text-sm text-destructive">{fieldErrors.slug}</p> : null}
          </div>

          <div className="space-y-2">
            <Label>{t('collections.fieldVisibility')}</Label>
            <Select
              value={visibility}
              disabled={!canEdit}
              onValueChange={(value) => {
                setVisibility(value as 'PUBLIC' | 'PRIVATE')
                clearFieldError('visibility')
              }}
            >
              <SelectTrigger aria-invalid={fieldErrors.visibility ? 'true' : 'false'}>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="PUBLIC">{t('collections.visibilityPublic')}</SelectItem>
                <SelectItem value="PRIVATE">{t('collections.visibilityPrivate')}</SelectItem>
              </SelectContent>
            </Select>
            {fieldErrors.visibility ? <p className="text-sm text-destructive">{fieldErrors.visibility}</p> : null}
          </div>

          <div className="flex gap-3">
            <Button type="submit" disabled={!canEdit || updateMutation.isPending || visibilityMutation.isPending}>
              {updateMutation.isPending || visibilityMutation.isPending ? t('collections.saving') : t('collections.save')}
            </Button>
          </div>
        </form>
      ) : null}
    </div>
  )
}
