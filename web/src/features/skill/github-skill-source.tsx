import { useTranslation } from 'react-i18next'
import type { GithubSkillSource } from '@/api/types'
import { Card } from '@/shared/ui/card'

export function GithubSkillSourceCard({ source, version }: { source: GithubSkillSource; version: string }) {
  const { t } = useTranslation()
  const repositoryUrl = `https://github.com/${encodeURIComponent(source.ownerLogin)}/${encodeURIComponent(source.repositoryName)}`
  const commitUrl = `${repositoryUrl}/commit/${encodeURIComponent(source.commitSha)}`

  return (
    <Card className="p-5 space-y-3">
      <h2 className="text-sm font-semibold font-heading text-foreground">{t('skillDetail.githubSourceTitle')}</h2>
      <p className="text-sm font-medium">{t('review.githubRevisionValue', { count: source.syncRevision })}</p>
      <details className="text-xs text-muted-foreground">
        <summary className="cursor-pointer">{t('review.technicalDetails')}</summary>
        <p className="mt-2 break-all font-mono">{version}</p>
      </details>
      <dl className="space-y-2 text-sm">
        <div><dt className="text-muted-foreground">{t('review.githubRepository')}</dt><dd className="break-all"><a data-analytics-id="github.repository.external" href={repositoryUrl} target="_blank" rel="noopener noreferrer" className="text-primary underline">{source.ownerLogin}/{source.repositoryName}</a></dd></div>
        <div><dt className="text-muted-foreground">{t('review.githubSkillPath')}</dt><dd className="font-mono break-all">{source.skillPath}</dd></div>
        <div><dt className="text-muted-foreground">{t('review.githubCommit')}</dt><dd className="break-all"><a data-analytics-id="github.commit.external" href={commitUrl} target="_blank" rel="noopener noreferrer" className="font-mono text-primary underline">{source.commitSha}</a></dd></div>
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
