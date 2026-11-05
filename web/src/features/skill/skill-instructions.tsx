import { useState, type MouseEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useSkillFile } from '@/shared/hooks/use-skill-queries'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { MarkdownRenderer } from './markdown-renderer'

interface SkillInstructionsProps {
  namespace: string
  slug: string
  version?: string
  available: boolean
  loading: boolean
  error: unknown
  retryFiles: () => void
  onLinkClick: (href: string, event: MouseEvent<HTMLAnchorElement>) => void
}

export function SkillInstructions({ namespace, slug, version, available, loading, error, retryFiles, onLinkClick }: SkillInstructionsProps) {
  const { t } = useTranslation()
  const [source, setSource] = useState(false)
  const query = useSkillFile(namespace, slug, version, 'SKILL.md', available && !error && !loading)
  if (error || query.error) {
    return (
      <Card className="p-6 space-y-3" role="alert">
        <p>{t('skillDetail.instructionsError')}</p>
        <Button data-analytics-id="skill.instructions.retry" variant="outline"
          onClick={() => error ? retryFiles() : void query.refetch()}>
          {t('skillDetail.instructionsRetry')}
        </Button>
      </Card>
    )
  }
  if (loading || (available && query.isLoading)) {
    return <Card className="p-6" role="status">{t('skillDetail.instructionsLoading')}</Card>
  }
  if (!available) {
    return <Card className="p-6">{t('skillDetail.instructionsMissing')}</Card>
  }
  return (
    <Card className="min-w-0 p-4 sm:p-6 space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border pb-4">
        <code className="text-sm text-muted-foreground">SKILL.md · {version}</code>
        <Button data-analytics-id="skill.instructions.source.toggle" variant="outline" size="sm"
          aria-pressed={source} onClick={() => setSource(!source)}>
          {t(source ? 'skillDetail.instructionsRendered' : 'skillDetail.instructionsSource')}
        </Button>
      </div>
      {source ? (
        <pre className="max-w-full overflow-x-auto whitespace-pre-wrap break-words text-sm">
          <code>{query.data}</code>
        </pre>
      ) : (
        <MarkdownRenderer content={query.data ?? ''} onLinkClick={onLinkClick} />
      )}
    </Card>
  )
}
