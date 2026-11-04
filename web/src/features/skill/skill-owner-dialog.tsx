import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { adminApi, namespaceApi, skillLifecycleApi } from '@/api/client'
import { Button } from '@/shared/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Textarea } from '@/shared/ui/textarea'
import { toast } from '@/shared/lib/toast'

// One page is enough for typical teams; namespaces past this size need a member search here.
const MEMBER_PAGE = { page: 0, size: 100 }

/**
 * Lets a namespace owner or super admin hand a skill to another member of its namespace.
 * Super admins outside the namespace read its members through the admin API.
 */
export function SkillOwnerDialog({ namespace, slug, ownerId, asPlatformAdmin = false }: {
  namespace: string; slug: string; ownerId: string; asPlatformAdmin?: boolean
}) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [newOwnerId, setNewOwnerId] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const members = useQuery({
    queryKey: [...(asPlatformAdmin ? ['admin'] : []), 'namespaces', namespace, 'members', MEMBER_PAGE],
    queryFn: () => asPlatformAdmin
      ? adminApi.getNamespaceMembers(namespace, MEMBER_PAGE)
      : namespaceApi.listMembers(namespace, MEMBER_PAGE),
    enabled: open,
  })
  const candidates = (members.data?.items ?? [])
    .filter(member => member.userId !== ownerId && member.active && !member.systemAccount)
  const mutation = useMutation({
    mutationFn: () => skillLifecycleApi.transferOwnership(namespace, slug, ownerId, newOwnerId, reason.trim()),
    onSuccess: async () => {
      toast.success(t('skillOwner.saved'))
      setOpen(false)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['skills'] }),
        queryClient.invalidateQueries({ queryKey: ['skill'] }),
        queryClient.invalidateQueries({ queryKey: ['my-skills'] }),
      ])
    },
    onError: (cause) => setError(cause.message),
  })
  return <>
    <Button data-analytics-id="skill.owner.open" size="sm" variant="outline" onClick={() => {
      setNewOwnerId('')
      setReason('')
      setError('')
      setOpen(true)
    }}>{t('skillOwner.title')}</Button>
    <Dialog open={open} onOpenChange={(value) => { if (!mutation.isPending) setOpen(value) }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('skillOwner.title')}</DialogTitle>
          <DialogDescription>{t('skillOwner.description')}</DialogDescription>
        </DialogHeader>
        {members.isError && <p role="alert" className="text-sm text-destructive">{t('skillOwner.membersFailed')}</p>}
        <div className="space-y-2">
          <Label htmlFor="skill-new-owner">{t('skillOwner.newOwner')}</Label>
          <Select value={newOwnerId} onValueChange={setNewOwnerId} disabled={mutation.isPending || !members.isSuccess}>
            <SelectTrigger data-analytics-id="skill.owner.interact" id="skill-new-owner"><SelectValue placeholder={t('skillOwner.selectPlaceholder')} /></SelectTrigger>
            <SelectContent>{candidates.map(member =>
              <SelectItem data-analytics-id="skill.owner.select" key={member.userId} value={member.userId}>{member.displayName || member.userId}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label htmlFor="skill-owner-reason">{t('skillOwner.reason')}</Label>
          <Textarea id="skill-owner-reason" value={reason} maxLength={500} disabled={mutation.isPending}
            placeholder={t('skillOwner.reasonPlaceholder')} onChange={(event) => setReason(event.target.value)} />
        </div>
        {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
        <DialogFooter>
          <Button data-analytics-id="skill.owner.cancel" variant="outline" disabled={mutation.isPending} onClick={() => setOpen(false)}>{t('dialog.cancel')}</Button>
          <Button data-analytics-id="skill.owner.confirm" disabled={mutation.isPending || !newOwnerId || !reason.trim()} onClick={() => mutation.mutate()}>{t('skillOwner.submit')}</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  </>
}
