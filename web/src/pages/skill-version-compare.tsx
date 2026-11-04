import { useCallback } from 'react'
import { useNavigate, useParams, useSearch } from '@tanstack/react-router'
import { SkillVersionCompareContent } from '@/features/skill/skill-version-compare-content'

export function SkillVersionComparePage() {
  const navigate = useNavigate()
  const { namespace, slug } = useParams({ from: '/space/$namespace/$slug/compare' })
  const { from, to } = useSearch({ from: '/space/$namespace/$slug/compare' })
  const onVersionsChange = useCallback((versions: { from: string; to: string }, replace = false) => {
    navigate({ to: '/space/$namespace/$slug/compare', params: { namespace, slug }, search: versions, replace })
  }, [navigate, namespace, slug])
  return <SkillVersionCompareContent namespace={namespace} slug={slug} from={from} to={to} onVersionsChange={onVersionsChange} />
}
