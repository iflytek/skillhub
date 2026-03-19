import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { CreateNamespaceRequest } from '@/api/types'
import { useCreateNamespace } from '@/shared/hooks/use-skill-queries'
import { toast } from '@/shared/lib/toast'
import { Button } from '@/shared/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Textarea } from '@/shared/ui/textarea'

interface CreateNamespaceDialogProps {
  children: React.ReactNode
}

type FieldErrors = {
  slug?: string
  displayName?: string
  description?: string
}

const SLUG_PATTERN = /^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/
const RESERVED_SLUGS = new Set([
  'admin',
  'api',
  'dashboard',
  'search',
  'auth',
  'me',
  'global',
  'system',
  'static',
  'assets',
  'health',
])
const MAX_SLUG_LENGTH = 64
const MIN_SLUG_LENGTH = 2
const MAX_DISPLAY_NAME_LENGTH = 128
const MAX_DESCRIPTION_LENGTH = 512

/**
 * Performs client-side validation that mirrors the backend namespace rules so
 * users get immediate feedback before the create request is sent.
 */
function buildFieldErrors(request: CreateNamespaceRequest, t: (key: string, options?: Record<string, unknown>) => string): FieldErrors {
  const errors: FieldErrors = {}
  const slug = request.slug.trim()
  const displayName = request.displayName.trim()
  const description = request.description?.trim() ?? ''

  if (!slug) {
    errors.slug = t('myNamespaces.createSlugRequired')
  } else if (slug.length < MIN_SLUG_LENGTH || slug.length > MAX_SLUG_LENGTH) {
    errors.slug = t('myNamespaces.createSlugLength', { min: MIN_SLUG_LENGTH, max: MAX_SLUG_LENGTH })
  } else if (!SLUG_PATTERN.test(slug)) {
    errors.slug = t('myNamespaces.createSlugPattern')
  } else if (slug.includes('--')) {
    errors.slug = t('myNamespaces.createSlugDoubleHyphen')
  } else if (RESERVED_SLUGS.has(slug)) {
    errors.slug = t('myNamespaces.createSlugReserved', { slug })
  }

  if (!displayName) {
    errors.displayName = t('myNamespaces.createDisplayNameRequired')
  } else if (displayName.length > MAX_DISPLAY_NAME_LENGTH) {
    errors.displayName = t('myNamespaces.createDisplayNameLength', { max: MAX_DISPLAY_NAME_LENGTH })
  }

  if (description.length > MAX_DESCRIPTION_LENGTH) {
    errors.description = t('myNamespaces.createDescriptionLength', { max: MAX_DESCRIPTION_LENGTH })
  }

  return errors
}

/**
 * Collects and validates namespace creation input before delegating the actual
 * mutation to the shared query layer. The dialog owns normalization because the
 * same slug/display-name constraints are also reflected in the local UI copy.
 */
export function CreateNamespaceDialog({ children }: CreateNamespaceDialogProps) {
  const { t } = useTranslation()
  const createMutation = useCreateNamespace()
  const [open, setOpen] = useState(false)
  const [slug, setSlug] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [description, setDescription] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})

  const resetDialog = () => {
    setSlug('')
    setDisplayName('')
    setDescription('')
    setErrors({})
    createMutation.reset()
  }

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
    if (!nextOpen) {
      resetDialog()
    }
  }

  const normalizedRequest: CreateNamespaceRequest = {
    slug: slug.trim().toLowerCase(),
    displayName: displayName.trim(),
    description: description.trim() || undefined,
  }

  const handleSubmit = async () => {
    const nextErrors = buildFieldErrors(normalizedRequest, t)
    if (Object.keys(nextErrors).length > 0) {
      setErrors(nextErrors)
      return
    }

    try {
      const namespace = await createMutation.mutateAsync(normalizedRequest)
      toast.success(
        t('myNamespaces.createSuccessTitle'),
        t('myNamespaces.createSuccessDescription', { name: namespace.displayName }),
      )
      handleOpenChange(false)
    } catch (error) {
      toast.error(t('myNamespaces.createErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  const slugLength = slug.trim().length
  const displayNameLength = displayName.trim().length
  const descriptionLength = description.trim().length

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader className="text-center sm:text-center">
          <DialogTitle className="text-center">{t('myNamespaces.createDialogTitle')}</DialogTitle>
          <DialogDescription className="text-center">
            {t('myNamespaces.createDialogDescription')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <div className="space-y-2">
            <Label htmlFor="namespace-slug">{t('myNamespaces.createSlugLabel')}</Label>
            <Input
              id="namespace-slug"
              placeholder={t('myNamespaces.createSlugPlaceholder')}
              value={slug}
              maxLength={MAX_SLUG_LENGTH}
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              onChange={(event) => {
                setSlug(event.target.value.toLowerCase())
                if (errors.slug) {
                  setErrors((current) => ({ ...current, slug: undefined }))
                }
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  handleSubmit()
                }
              }}
              aria-invalid={errors.slug ? 'true' : 'false'}
            />
            <div className="flex items-center justify-between gap-3 text-xs">
              <span className="text-red-600">{errors.slug ?? t('myNamespaces.createSlugHint')}</span>
              <span className="text-muted-foreground">{slugLength}/{MAX_SLUG_LENGTH}</span>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="namespace-display-name">{t('myNamespaces.createDisplayNameLabel')}</Label>
            <Input
              id="namespace-display-name"
              placeholder={t('myNamespaces.createDisplayNamePlaceholder')}
              value={displayName}
              maxLength={MAX_DISPLAY_NAME_LENGTH}
              onChange={(event) => {
                setDisplayName(event.target.value)
                if (errors.displayName) {
                  setErrors((current) => ({ ...current, displayName: undefined }))
                }
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter' && !event.shiftKey) {
                  event.preventDefault()
                  handleSubmit()
                }
              }}
              aria-invalid={errors.displayName ? 'true' : 'false'}
            />
            <div className="flex items-center justify-between gap-3 text-xs">
              <span className="text-red-600">{errors.displayName ?? ''}</span>
              <span className="text-muted-foreground">{displayNameLength}/{MAX_DISPLAY_NAME_LENGTH}</span>
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="namespace-description">{t('myNamespaces.createDescriptionLabel')}</Label>
            <Textarea
              id="namespace-description"
              placeholder={t('myNamespaces.createDescriptionPlaceholder')}
              value={description}
              maxLength={MAX_DESCRIPTION_LENGTH}
              onChange={(event) => {
                setDescription(event.target.value)
                if (errors.description) {
                  setErrors((current) => ({ ...current, description: undefined }))
                }
              }}
              aria-invalid={errors.description ? 'true' : 'false'}
            />
            <div className="flex items-center justify-between gap-3 text-xs">
              <span className="text-red-600">{errors.description ?? t('myNamespaces.createDescriptionHint')}</span>
              <span className="text-muted-foreground">{descriptionLength}/{MAX_DESCRIPTION_LENGTH}</span>
            </div>
          </div>
        </div>

        {createMutation.error ? (
          <p className="text-sm text-red-600">{createMutation.error.message}</p>
        ) : null}

        <DialogFooter className="sm:justify-center sm:space-x-3">
          <Button variant="outline" onClick={() => handleOpenChange(false)}>
            {t('dialog.cancel')}
          </Button>
          <Button onClick={handleSubmit} disabled={createMutation.isPending}>
            {createMutation.isPending ? t('myNamespaces.creating') : t('myNamespaces.createSubmit')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
