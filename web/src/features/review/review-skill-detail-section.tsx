import { useState } from 'react'
import { ChevronDown, ChevronUp } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { buildApiUrl } from '@/api/client'
import type { ReviewSkillDetail } from '@/api/types'
import { FileTree } from '@/features/skill/file-tree'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { getReviewDownloadHref, getReviewSkillDocumentation, isActiveReviewVersion } from './review-skill-detail'

interface ReviewSkillDetailSectionProps {
  detail?: ReviewSkillDetail
  isLoading?: boolean
  hasError?: boolean
}

export function ReviewSkillDetailSection({ detail, isLoading, hasError }: ReviewSkillDetailSectionProps) {
  const { t } = useTranslation()
  const [isExpanded, setIsExpanded] = useState(false)

  if (isLoading) {
    return (
      <Card className="p-8 space-y-4">
        <div className="h-8 w-56 animate-shimmer rounded-lg" />
        <div className="h-48 animate-shimmer rounded-xl" />
      </Card>
    )
  }

  if (hasError) {
    return (
      <Card className="p-8 space-y-2">
        <h2 className="text-xl font-bold font-heading">{t('review.skillDetailTitle')}</h2>
        <p className="text-sm text-muted-foreground">{t('review.skillDetailError')}</p>
      </Card>
    )
  }

  if (!detail) {
    return null
  }

  const documentation = getReviewSkillDocumentation(detail)

  return (
    <Card className="p-6 space-y-4">
      <button
        type="button"
        className="flex w-full items-start justify-between gap-4 text-left"
        aria-expanded={isExpanded}
        onClick={() => setIsExpanded((current) => !current)}
      >
        <div className="space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-xl font-bold font-heading">{t('review.skillDetailTitle')}</h2>
            <span className="inline-flex items-center rounded-full bg-secondary px-2.5 py-0.5 text-xs font-medium text-secondary-foreground">
              {t('review.activeReviewVersion')}
            </span>
            <span className="inline-flex items-center rounded-full border border-border px-2.5 py-0.5 text-xs font-medium font-mono text-foreground">
              {detail.activeVersion}
            </span>
          </div>
          <p className="text-sm text-muted-foreground">
            {t('review.skillDetailDescription', {
              skill: `${detail.skill.namespace}/${detail.skill.slug}`,
            })}
          </p>
        </div>
        <span className="mt-1 shrink-0 rounded-full border border-border/70 p-2 text-muted-foreground">
          {isExpanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
        </span>
      </button>

      {isExpanded ? (
        <div data-review-skill-detail-panel className="space-y-6 border-t border-border/60 pt-4">
          <div className="flex justify-start">
            <a className={buttonVariants()} href={buildApiUrl(getReviewDownloadHref(detail))}>
              {t('review.downloadSkillZip')}
            </a>
          </div>

          <Tabs defaultValue="overview" className="space-y-4">
            <TabsList>
              <TabsTrigger value="overview">{t('skillDetail.tabOverview')}</TabsTrigger>
              <TabsTrigger value="files">{t('skillDetail.tabFiles')}</TabsTrigger>
              <TabsTrigger value="versions">{t('skillDetail.tabVersions')}</TabsTrigger>
            </TabsList>

            <TabsContent value="overview" className="space-y-4">
              {documentation ? (
                <div className="space-y-3">
                  <p className="text-sm font-mono text-muted-foreground">{documentation.path}</p>
                  <div className="rounded-2xl border border-border/60 bg-card/60 p-6">
                    <MarkdownRenderer content={documentation.content} />
                  </div>
                </div>
              ) : (
                <div className="rounded-2xl border border-dashed border-border/70 bg-muted/20 p-6 text-sm text-muted-foreground">
                  {t('review.noDocumentation')}
                </div>
              )}
            </TabsContent>

            <TabsContent value="files">
              {detail.files.length > 0 ? (
                <FileTree files={detail.files} />
              ) : (
                <div className="rounded-2xl border border-dashed border-border/70 bg-muted/20 p-6 text-sm text-muted-foreground">
                  {t('skillDetail.noFiles')}
                </div>
              )}
            </TabsContent>

            <TabsContent value="versions">
              {detail.versions.length > 0 ? (
                <div className="space-y-3">
                  {detail.versions.map((version) => (
                    <div
                      key={version.id}
                      className="flex flex-col gap-3 rounded-2xl border border-border/70 bg-card/70 p-4 md:flex-row md:items-center md:justify-between"
                    >
                      <div className="space-y-2">
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-semibold font-mono">{version.version}</span>
                          <span className="inline-flex items-center rounded-full border border-border px-2.5 py-0.5 text-xs font-medium text-foreground">
                            {version.status}
                          </span>
                          {isActiveReviewVersion(version, detail) ? (
                            <span className="inline-flex items-center rounded-full bg-brand-gradient px-2.5 py-0.5 text-xs font-medium text-white">
                              {t('review.activeReviewVersion')}
                            </span>
                          ) : null}
                        </div>
                        {version.changelog ? (
                          <p className="text-sm text-muted-foreground">{version.changelog}</p>
                        ) : null}
                      </div>
                      <div className="text-sm text-muted-foreground">
                        {t('skillDetail.fileCount', { count: version.fileCount })}
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="rounded-2xl border border-dashed border-border/70 bg-muted/20 p-6 text-sm text-muted-foreground">
                  {t('skillDetail.noVersions')}
                </div>
              )}
            </TabsContent>
          </Tabs>

          <div className="flex justify-start">
            <Button
              type="button"
              variant="outline"
              size="sm"
              className="gap-2 rounded-full border-border/70 bg-background/90 px-5 shadow-sm backdrop-blur-sm"
              aria-expanded={isExpanded}
              onClick={() => setIsExpanded(false)}
            >
              <ChevronUp className="h-4 w-4" />
              {t('skillDetail.collapseOverview')}
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex justify-start">
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="gap-2 rounded-full border-border/70 bg-background/90 px-5 shadow-sm backdrop-blur-sm"
            aria-expanded={isExpanded}
            onClick={() => setIsExpanded(true)}
          >
            <ChevronDown className="h-4 w-4" />
            {t('skillDetail.expandOverview')}
          </Button>
        </div>
      )}
    </Card>
  )
}
