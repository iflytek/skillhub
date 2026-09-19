import { useState } from 'react'
import { Link } from '@tanstack/react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Plus } from 'lucide-react'
import { authoringApi, namespaceApi } from '@/api/client'
import type { AuthoringDraft } from '@/api/types'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'
import { Button } from '@/shared/ui/button'
import { Card, CardContent } from '@/shared/ui/card'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/shared/ui/table'
import { Textarea } from '@/shared/ui/textarea'
import { toast } from '@/shared/lib/toast'
import { formatLocalDateTime } from '@/shared/lib/date-time'

/**
 * Authoring workbench entry: the user's skill drafts with validation state and
 * quick access to the editor, plus draft creation.
 */
export function DraftsPage() {
  const { t, i18n } = useTranslation()
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<AuthoringDraft | null>(null)

  const drafts = useQuery({
    queryKey: ['authoring', 'drafts'],
    queryFn: () => authoringApi.listDrafts(),
  })

  const createDraft = useMutation({
    mutationFn: (request: { namespaceSlug: string, name: string, requirement?: string }) =>
      authoringApi.createDraft(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['authoring', 'drafts'] })
      setCreateOpen(false)
      toast.success(t('authoring.list.created'))
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : String(error))
    },
  })

  const deleteDraft = useMutation({
    mutationFn: (draftId: number) => authoringApi.deleteDraft(draftId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['authoring', 'drafts'] })
      setDeleteTarget(null)
      toast.success(t('authoring.list.deleted'))
    },
    onError: (error) => {
      toast.error(error instanceof Error ? error.message : String(error))
    },
  })

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('authoring.list.title')}
        subtitle={t('authoring.list.subtitle')}
        actions={
          <Button onClick={() => setCreateOpen(true)} data-testid="create-draft">
            <Plus className="mr-1 h-4 w-4" aria-hidden />
            {t('authoring.list.create')}
          </Button>
        }
      />

      <Card>
        <CardContent className="pt-6">
          {drafts.isLoading && <p className="text-sm text-muted-foreground">{t('authoring.ui.loading')}</p>}
          {drafts.isError && <p className="text-sm text-red-600">{t('apiError.networkError')}</p>}
          {drafts.data && drafts.data.length === 0 && (
            <p className="py-8 text-center text-sm text-muted-foreground">{t('authoring.list.empty')}</p>
          )}
          {drafts.data && drafts.data.length > 0 && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('authoring.list.colName')}</TableHead>
                  <TableHead>{t('authoring.list.colRevision')}</TableHead>
                  <TableHead>{t('authoring.list.colValidated')}</TableHead>
                  <TableHead>{t('authoring.list.colSubmitted')}</TableHead>
                  <TableHead>{t('authoring.list.colUpdated')}</TableHead>
                  <TableHead className="w-32">{t('authoring.list.colActions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {drafts.data.map((draft) => (
                  <TableRow key={draft.id} data-testid={`draft-row-${draft.id}`}>
                    <TableCell>
                      <Link
                        to="/dashboard/authoring/$draftId"
                        params={{ draftId: String(draft.id) }}
                        className="font-medium text-blue-600 hover:underline dark:text-blue-400"
                      >
                        {draft.name}
                      </Link>
                      {draft.requirement && (
                        <p className="max-w-md truncate text-xs text-muted-foreground">{draft.requirement}</p>
                      )}
                    </TableCell>
                    <TableCell>#{draft.revision}</TableCell>
                    <TableCell>
                      {draft.validated ? (
                        <span className="rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-950 dark:text-green-300">
                          {t('authoring.list.validated')}
                        </span>
                      ) : (
                        <span className="rounded-full bg-muted px-2 py-0.5 text-xs font-medium text-muted-foreground">
                          {t('authoring.list.notValidated')}
                        </span>
                      )}
                    </TableCell>
                    <TableCell>
                      {draft.submittedSkillId != null
                        ? t('authoring.list.submittedAt', { version: draft.submittedVersionId ?? '' })
                        : '—'}
                    </TableCell>
                    <TableCell>{draft.updatedAt ? formatLocalDateTime(draft.updatedAt, i18n.language) : '—'}</TableCell>
                    <TableCell>
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => setDeleteTarget(draft)}
                        data-testid={`delete-draft-${draft.id}`}
                      >
                        {t('authoring.ui.delete')}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <CreateDraftDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        submitting={createDraft.isPending}
        onSubmit={(request) => createDraft.mutate(request)}
      />

      <ConfirmDialog
        open={deleteTarget != null}
        onOpenChange={(open) => {
          if (!open) {
            setDeleteTarget(null)
          }
        }}
        title={t('authoring.list.deleteTitle')}
        description={t('authoring.list.deleteDescription', { name: deleteTarget?.name ?? '' })}
        variant="destructive"
        onConfirm={() => (deleteTarget ? deleteDraft.mutateAsync(deleteTarget.id) : Promise.resolve())}
      />
    </div>
  )
}

function CreateDraftDialog({
  open,
  onOpenChange,
  submitting,
  onSubmit,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  submitting: boolean
  onSubmit: (request: { namespaceSlug: string, name: string, requirement?: string }) => void
}) {
  const { t } = useTranslation()
  const [name, setName] = useState('')
  const [namespaceSlug, setNamespaceSlug] = useState('')
  const [requirement, setRequirement] = useState('')

  const namespaces = useQuery({
    queryKey: ['authoring', 'namespaces'],
    queryFn: () => namespaceApi.listMine(),
    enabled: open,
  })

  const submit = () => {
    if (!namespaceSlug || !name.trim()) {
      return
    }
    onSubmit({ namespaceSlug, name: name.trim(), requirement: requirement.trim() || undefined })
  }

  return (
    <Dialog
      open={open}
      onOpenChange={(nextOpen) => {
        if (!nextOpen) {
          setName('')
          setNamespaceSlug('')
          setRequirement('')
        }
        onOpenChange(nextOpen)
      }}
    >
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('authoring.list.createTitle')}</DialogTitle>
          <DialogDescription>{t('authoring.list.createDescription')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-4">
          <div className="grid gap-2">
            <Label htmlFor="draft-namespace">{t('authoring.list.fieldNamespace')}</Label>
            <Select value={namespaceSlug} onValueChange={setNamespaceSlug}>
              <SelectTrigger id="draft-namespace" data-testid="draft-namespace">
                <SelectValue placeholder={t('authoring.list.namespacePlaceholder')} />
              </SelectTrigger>
              <SelectContent>
                {(namespaces.data ?? []).map((namespace) => (
                  <SelectItem key={namespace.slug} value={namespace.slug}>
                    {namespace.displayName || namespace.slug}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="draft-name">{t('authoring.list.fieldName')}</Label>
            <Input
              id="draft-name"
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="document-summarizer"
              data-testid="draft-name"
            />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="draft-requirement">{t('authoring.list.fieldRequirement')}</Label>
            <Textarea
              id="draft-requirement"
              rows={3}
              value={requirement}
              onChange={(event) => setRequirement(event.target.value)}
              placeholder={t('authoring.list.requirementPlaceholder')}
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            {t('authoring.ui.cancel')}
          </Button>
          <Button onClick={submit} disabled={submitting || !namespaceSlug || !name.trim()} data-testid="submit-create-draft">
            {submitting ? t('authoring.ui.submitting') : t('authoring.ui.create')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
