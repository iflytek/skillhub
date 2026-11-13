import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { BookOpen, Check, ImageOff, FileText, ListChecks, Pencil, Search, ShieldCheck } from 'lucide-react'
import { presentationAssetUrl } from '@/api/site-presentation'
import { cn } from '@/shared/lib/utils'
import './recommendations.css'

export const RECOMMENDATION_COVER_STYLES = ['office', 'design', 'learning', 'content', 'research', 'security'] as const
export type RecommendationCoverStyle = typeof RECOMMENDATION_COVER_STYLES[number]
const presetStyles: Record<string, RecommendationCoverStyle> = {
  'daily-work': 'office', 'design-docs': 'design', learning: 'learning', writing: 'content', research: 'research', 'safe-tools': 'security',
}

/** Old saved topics keep a stable cover; an explicit editorial choice always wins. */
export function recommendationCoverStyle(topicId: string, coverStyle?: string | null): RecommendationCoverStyle {
  const selected = RECOMMENDATION_COVER_STYLES.find(style => style === coverStyle)
  if (selected) return selected
  if (Object.hasOwn(presetStyles, topicId)) return presetStyles[topicId]
  let hash = 0
  for (const char of topicId) hash = (Math.imul(hash, 31) + char.codePointAt(0)!) | 0
  return RECOMMENDATION_COVER_STYLES[(hash >>> 0) % RECOMMENDATION_COVER_STYLES.length]
}

function Lines() {
  return <div className="recommendation-art-lines"><i /><i /><i /></div>
}

/** Decorative task diagrams, never skill avatars; business text comes only from the actual topic and associated skills. */
export function RecommendationCover({ topicId, coverStyle, coverUrl, className, title, skillNames = [] }: {
  title?: string; skillNames?: string[]; topicId: string; coverStyle?: string | null; coverUrl?: string | null; className?: string
}) {
  const { t } = useTranslation()
  const style = recommendationCoverStyle(topicId, coverStyle)
  const src = presentationAssetUrl(coverUrl)
  const [failedSource, setFailedSource] = useState<string>()
  const image = src && failedSource !== src
  return <div className={cn('recommendation-cover', `recommendation-cover--${style}`, className)} aria-hidden="true" data-cover-style={style} data-custom-image={!!src || undefined}>
    {image ? <img src={src} alt="" onError={() => setFailedSource(src)} /> : src ? <div className="recommendation-cover-fallback"><ImageOff aria-hidden="true" /><strong>{title}</strong><span>{t('recommendations.coverUnavailable')}</span></div> : <>
      <strong className="recommendation-cover-heading">{title}</strong>
      <div className="recommendation-art-row">
        {style === 'office' ? <>
          <div className="recommendation-art-paper"><strong>{skillNames[0]}</strong><Lines /></div>
          <div className="recommendation-art-stack"><div><Check /><span>{skillNames[1]}</span></div><div><Check /><span>{skillNames[2]}</span></div></div>
        </> : style === 'design' ? <>
          <div className="recommendation-art-browser"><div className="recommendation-art-dots"><i /><i /><i /></div><div className="recommendation-art-layout"><i /><span><b /><em /><em /></span></div></div>
          <div className="recommendation-art-paper"><FileText /><strong>{skillNames[0]}</strong><Lines /></div>
        </> : style === 'content' || style === 'security' ? <>
          <div className="recommendation-art-paper"><strong>{skillNames[0]}</strong><Lines /></div>
          <div className="recommendation-art-paper">{style === 'content' ? <Pencil /> : <ShieldCheck />}<strong>{skillNames[1]}</strong><Lines /></div>
        </> : <>
          <div className="recommendation-art-paper recommendation-art-pair">{style === 'learning' ? <BookOpen /> : <Search />}<span><strong>{skillNames[0]}</strong><Lines /></span></div>
          <div className="recommendation-art-paper recommendation-art-pair">{style === 'learning' ? <ListChecks /> : <Check />}<span><strong>{skillNames[1]}</strong><Lines /></span></div>
        </>}
      </div>
    </>}
  </div>
}
