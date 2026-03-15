import { useParams } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { NamespaceHeader } from '@/features/namespace/namespace-header'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { useNamespaceDetail, useNamespaceMembers } from '@/shared/hooks/use-skill-queries'

export function NamespaceMembersPage() {
  const translation = useTranslation()
  const t = translation.t
  const language = translation.i18n.language
  const params = useParams({ from: '/dashboard/namespaces/$slug/members' })
  const slug = params.slug

  const { data: namespace, isLoading: isLoadingNamespace } = useNamespaceDetail(slug)
  const { data: members, isLoading: isLoadingMembers } = useNamespaceMembers(slug)

  if (isLoadingNamespace) {
    return (
      <div className="space-y-6 animate-fade-up">
        <div className="h-12 w-48 animate-shimmer rounded-lg" />
        <div className="h-6 w-96 animate-shimmer rounded-md" />
      </div>
    )
  }

  if (!namespace) {
    return (
      <div className="text-center py-20 animate-fade-up">
        <h2 className="text-2xl font-bold font-heading mb-2">{t('members.namespaceNotFound')}</h2>
      </div>
    )
  }

  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader
        title={t('members.title')}
        subtitle={namespace ? `@${namespace.slug}` : undefined}
      />
      <NamespaceHeader namespace={namespace} />

      <div className="space-y-6">
        <div className="flex items-center justify-end">
          <Button disabled>{t('members.addMember')}</Button>
        </div>

        {isLoadingMembers ? (
          <div className="space-y-3">
            {Array.from({ length: 3 }).map((_, i) => (
              <div key={i} className="h-14 animate-shimmer rounded-lg" />
            ))}
          </div>
        ) : members && members.length > 0 ? (
          <Card className="overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full">
                <thead>
                  <tr className="border-b border-border/40">
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colUserId')}</th>
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colRole')}</th>
                    <th className="text-left p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colJoinedAt')}</th>
                    <th className="text-right p-4 font-medium font-heading text-sm text-muted-foreground">{t('members.colActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {members.map((member) => (
                    <tr key={member.id} className="border-b border-border/40 last:border-b-0 hover:bg-secondary/30 transition-colors">
                      <td className="p-4 font-medium">{member.userId}</td>
                      <td className="p-4">
                        <span className="inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium bg-accent/10 text-accent border border-accent/20">
                          {member.role}
                        </span>
                      </td>
                      <td className="p-4 text-sm text-muted-foreground">
                        {new Date(member.createdAt).toLocaleDateString(language)}
                      </td>
                      <td className="p-4 text-right">
                        <Button variant="destructive" size="sm" disabled>
                          {t('members.remove')}
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>
        ) : (
          <Card className="p-6 text-center text-muted-foreground">
            {t('members.empty')}
          </Card>
        )}
      </div>
    </div>
  )
}
