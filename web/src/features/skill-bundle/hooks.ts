import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { skillBundleApi, type BuildDraftPayload, type SkillBundleDetail } from './api'

const SKILL_BUNDLE_KEY = ['skill-bundles'] as const

export function useSkillBundleDetail(namespace: string, slug: string, version?: string) {
  return useQuery<SkillBundleDetail>({
    queryKey: [...SKILL_BUNDLE_KEY, namespace, slug, version ?? 'latest'],
    queryFn: () => skillBundleApi.getDetail(namespace, slug, version),
    enabled: !!namespace && !!slug,
  })
}

export function useBuildSkillBundleDraft(namespace: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (payload: BuildDraftPayload) => skillBundleApi.buildDraft(namespace, payload),
    onSuccess: () => qc.invalidateQueries({ queryKey: SKILL_BUNDLE_KEY }),
  })
}

export function useApproveBundleReview() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => skillBundleApi.approveReview(id, comment),
    onSuccess: () => qc.invalidateQueries({ queryKey: SKILL_BUNDLE_KEY }),
  })
}

export function useRejectBundleReview() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => skillBundleApi.rejectReview(id, comment),
    onSuccess: () => qc.invalidateQueries({ queryKey: SKILL_BUNDLE_KEY }),
  })
}
