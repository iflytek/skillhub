import { GitFork, ExternalLink, FolderGit2 } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { GithubSkillSource } from '@/api/types'
import { Card } from '@/shared/ui/card'

export function GithubSkillSourceCard({ source, version }: { source: GithubSkillSource; version: string }) {
  const { t } = useTranslation()
  const repositoryUrl = `https://github.com/${encodeURIComponent(source.ownerLogin)}/${encodeURIComponent(source.repositoryName)}`
  const commitUrl = `${repositoryUrl}/commit/${encodeURIComponent(source.commitSha)}`
  // Servers without source tag support omit the field; show it like an unrecorded snapshot.
  const upstreamTag = source.upstreamTag ?? { status: 'UNRECORDED', scope: 'REPOSITORY' }

  return (
    <Card className="p-5 space-y-3">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className="flex items-center gap-2 text-sm font-semibold font-heading text-foreground"><GitFork aria-hidden="true" className="h-4 w-4" />{t('skillDetail.githubSourceTitle')}</h2>
        <a href={repositoryUrl} target="_blank" rel="noopener noreferrer" data-analytics-id="github.repository.external" className="inline-flex min-h-11 items-center gap-2 rounded-lg border px-3 text-sm hover:bg-secondary focus-visible:outline focus-visible:outline-2 focus-visible:outline-ring sm:min-h-8">{t('reviewGroups.sourceRepository')}<ExternalLink aria-hidden="true" className="h-3.5 w-3.5" /></a>
      </div>
      <a href={`${repositoryUrl}/tree/${encodeURIComponent(source.commitSha)}/${source.skillPath.split('/').map(encodeURIComponent).join('/')}`} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-2 text-sm text-primary underline"><FolderGit2 aria-hidden="true" className="h-4 w-4" />{t('reviewGroups.sourceDirectory')}</a>
      <p className="text-sm font-medium">{t('review.githubRevisionValue', { count: source.syncRevision })}</p>
      <details className="text-xs text-muted-foreground">
        <summary className="cursor-pointer">{t('review.technicalDetails')}</summary>
        <p className="mt-2 break-all font-mono">{version}</p>
      </details>
      <dl className="space-y-2 text-sm">
        <div><dt className="text-muted-foreground">{t('review.githubRepository')}</dt><dd className="break-all"><a data-analytics-id="github.repository.external" href={repositoryUrl} target="_blank" rel="noopener noreferrer" className="text-primary underline">{source.ownerLogin}/{source.repositoryName}</a></dd></div>
        <div><dt className="text-muted-foreground">{t('review.githubSkillPath')}</dt><dd className="font-mono break-all">{source.skillPath}</dd></div>
        <div><dt className="text-muted-foreground">{t('review.githubCommit')}</dt><dd className="break-all"><a data-analytics-id="github.commit.external" href={commitUrl} target="_blank" rel="noopener noreferrer" className="font-mono text-primary underline">{source.commitSha}</a></dd></div>
        <div>
          <dt className="text-muted-foreground">
            {t('skillDetail.githubUpstreamTag')}
            <span className="ml-1 text-xs">({t('skillDetail.githubUpstreamTagScope')})</span>
          </dt>
          <dd className="break-all">
            {t(`skillDetail.githubUpstreamTag${upstreamTag.status}`, {
              tag: upstreamTag.tag ?? '',
              count: upstreamTag.commitsAfterTag ?? 0,
              commit: source.commitSha.slice(0, 8),
              defaultValue: upstreamTag.status,
            })}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">{t('skillDetail.githubDeclaredVersion')}</dt>
          <dd className="font-mono break-all">{source.declaredVersion ?? t('skillDetail.githubDeclaredVersionNone')}</dd>
        </div>
        <div><dt className="text-muted-foreground">{t('review.githubLicenseStatus')}</dt><dd>{t(`review.githubLicense${source.licenseStatus}`, { defaultValue: source.licenseStatus })}</dd></div>
      </dl>
      <div>
        <h3 className="text-sm font-medium">{t('review.githubLicenseDocuments')}</h3>
        {source.licenseDocuments.length === 0 ? (
          <p className="mt-1 text-sm text-muted-foreground">{t('review.githubNoLicenseDocuments')}</p>
        ) : (
          <ul className="mt-1 space-y-1 text-sm">
            {source.licenseDocuments.map((document) => {
              const repositoryPath = document.scope === 'SKILL_ROOT' && source.skillPath !== '.'
                ? `${source.skillPath}/${document.path}` : document.path
              const fileUrl = `${repositoryUrl}/blob/${encodeURIComponent(source.commitSha)}/${repositoryPath.split('/').map(encodeURIComponent).join('/')}`
              return (
                <li key={`${document.scope}/${document.path}`} className="break-all">
                  <span className="text-muted-foreground">{t(`skillDetail.githubLicenseScope${document.scope}`)}: </span>
                  <a data-analytics-id="github.file.external" href={fileUrl} target="_blank" rel="noopener noreferrer" className="font-mono text-primary underline">{repositoryPath}</a>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </Card>
  )
}
