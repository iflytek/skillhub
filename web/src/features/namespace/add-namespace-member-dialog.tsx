import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { NamespaceRole } from '@/api/types'
import { useAddNamespaceMember, useNamespaceMemberCandidates } from '@/shared/hooks/use-namespace-queries'
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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/shared/ui/select'

interface AddNamespaceMemberDialogProps {
  slug: string
  children: React.ReactNode
}

const ROLE_OPTIONS: NamespaceRole[] = ['MEMBER', 'ADMIN']

/**
 * Handles the namespace member invitation flow, including optional candidate
 * lookup and direct user-id entry. Local state is reset on close so reopening
 * the dialog never leaks stale search or validation state from prior attempts.
 */
export function AddNamespaceMemberDialog({ slug, children }: AddNamespaceMemberDialogProps) {
  const { t } = useTranslation()
  const addMemberMutation = useAddNamespaceMember()
  const [open, setOpen] = useState(false)
  const [searchInput, setSearchInput] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [userId, setUserId] = useState('')
  const [role, setRole] = useState<NamespaceRole>('MEMBER')
  const [userIdError, setUserIdError] = useState<string | null>(null)
  const [searchError, setSearchError] = useState<string | null>(null)

  const { data: candidates, isFetching, error: candidatesError } = useNamespaceMemberCandidates(
    slug,
    appliedSearch,
    open,
  )

  const resetDialog = () => {
    setSearchInput('')
    setAppliedSearch('')
    setUserId('')
    setRole('MEMBER')
    setUserIdError(null)
    setSearchError(null)
    addMemberMutation.reset()
  }

  const handleOpenChange = (nextOpen: boolean) => {
    setOpen(nextOpen)
    if (!nextOpen) {
      resetDialog()
    }
  }

  const handleSearch = () => {
    const keyword = searchInput.trim()
    // Short keywords usually generate low-signal candidate lists and create
    // needless backend traffic, so the UI enforces a small minimum length.
    if (keyword.length > 0 && keyword.length < 2) {
      setSearchError(t('members.searchTooShort'))
      return
    }
    setSearchError(null)
    setAppliedSearch(keyword)
  }

  const handleAddMember = async () => {
    const normalizedUserId = userId.trim()
    if (!normalizedUserId) {
      setUserIdError(t('members.userIdRequired'))
      return
    }

    try {
      await addMemberMutation.mutateAsync({
        slug,
        userId: normalizedUserId,
        role,
      })
      toast.success(
        t('members.addSuccessTitle'),
        t('members.addSuccessDescription', { userId: normalizedUserId }),
      )
      handleOpenChange(false)
    } catch (error) {
      toast.error(t('members.addErrorTitle'), error instanceof Error ? error.message : '')
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogTrigger asChild>{children}</DialogTrigger>
      <DialogContent>
        <DialogHeader className="text-center sm:text-center">
          <DialogTitle className="text-center">{t('members.addDialogTitle')}</DialogTitle>
          <DialogDescription className="text-center">
            {t('members.addDialogDescription')}
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-5 py-2">
          <div className="space-y-2">
            <Label htmlFor="member-search">{t('members.searchLabel')}</Label>
            <div className="flex gap-2">
              <Input
                id="member-search"
                value={searchInput}
                placeholder={t('members.searchPlaceholder')}
                onChange={(event) => {
                  setSearchInput(event.target.value)
                  if (searchError) {
                    setSearchError(null)
                  }
                }}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault()
                    handleSearch()
                  }
                }}
              />
              <Button type="button" variant="outline" onClick={handleSearch}>
                {t('members.searchAction')}
              </Button>
            </div>
            <p className={`text-xs ${searchError ? 'text-red-600' : 'text-muted-foreground'}`}>
              {searchError ?? t('members.searchHint')}
            </p>
          </div>

          {appliedSearch ? (
            <div className="space-y-2 rounded-xl border border-border/50 bg-secondary/30 p-3">
              <div className="text-sm font-medium">{t('members.searchResultsTitle')}</div>
              {isFetching ? (
                <div className="space-y-2">
                  {Array.from({ length: 2 }).map((_, index) => (
                    <div key={index} className="h-16 animate-shimmer rounded-lg" />
                  ))}
                </div>
              ) : candidatesError ? (
                <p className="text-sm text-red-600">{candidatesError.message}</p>
              ) : candidates && candidates.length > 0 ? (
                <div className="space-y-2">
                  {candidates.map((candidate) => (
                    <div
                      key={candidate.userId}
                      className="flex items-start justify-between gap-3 rounded-lg border border-border/50 bg-background/80 p-3"
                    >
                      <div className="min-w-0 space-y-1">
                        <div className="font-medium">{candidate.displayName}</div>
                        <div className="text-sm text-muted-foreground">{candidate.email || candidate.userId}</div>
                        <div className="font-mono text-xs text-muted-foreground">{candidate.userId}</div>
                      </div>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => {
                          setUserId(candidate.userId)
                          setUserIdError(null)
                        }}
                      >
                        {t('members.selectCandidate')}
                      </Button>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">{t('members.searchEmpty')}</p>
              )}
            </div>
          ) : null}

          <div className="space-y-2">
            <Label htmlFor="member-user-id">{t('members.manualUserIdLabel')}</Label>
            <Input
              id="member-user-id"
              value={userId}
              placeholder={t('members.manualUserIdPlaceholder')}
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              onChange={(event) => {
                setUserId(event.target.value)
                if (userIdError) {
                  setUserIdError(null)
                }
              }}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault()
                  handleAddMember()
                }
              }}
              aria-invalid={userIdError ? 'true' : 'false'}
            />
            <p className={`text-xs ${userIdError ? 'text-red-600' : 'text-muted-foreground'}`}>
              {userIdError ?? t('members.manualUserIdHint')}
            </p>
          </div>

          <div className="space-y-2">
            <Label htmlFor="member-role">{t('members.roleLabel')}</Label>
            <Select value={role} onValueChange={(value) => setRole(value as NamespaceRole)}>
              <SelectTrigger id="member-role">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {ROLE_OPTIONS.map((option) => (
                  <SelectItem key={option} value={option}>
                    {t(option === 'ADMIN' ? 'members.roleAdmin' : 'members.roleMember')}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        {addMemberMutation.error ? (
          <p className="text-sm text-red-600">{addMemberMutation.error.message}</p>
        ) : null}

        <DialogFooter className="sm:justify-center sm:space-x-3">
          <Button type="button" variant="outline" onClick={() => handleOpenChange(false)}>
            {t('dialog.cancel')}
          </Button>
          <Button type="button" onClick={handleAddMember} disabled={addMemberMutation.isPending}>
            {addMemberMutation.isPending ? t('members.addingMember') : t('members.addMember')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
