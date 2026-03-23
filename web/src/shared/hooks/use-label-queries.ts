import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import type { LabelItem, LabelDefinition } from '@/api/types'
import { labelApi } from '@/api/client'
import { getVisibleLabelsQueryKey, getSkillLabelsQueryKey, getAdminLabelDefinitionsQueryKey } from './query-keys'

async function getVisibleLabels(): Promise<LabelItem[]> {
  return labelApi.listVisible()
}

async function getSkillLabels(namespace: string, slug: string): Promise<LabelItem[]> {
  return labelApi.listSkillLabels(namespace, slug)
}

async function getAdminLabelDefinitions(): Promise<LabelDefinition[]> {
  return labelApi.listAdminDefinitions()
}

async function attachSkillLabel(params: { namespace: string; slug: string; labelSlug: string }): Promise<LabelItem> {
  return labelApi.attachSkillLabel(params.namespace, params.slug, params.labelSlug)
}

async function detachSkillLabel(params: { namespace: string; slug: string; labelSlug: string }): Promise<void> {
  return labelApi.detachSkillLabel(params.namespace, params.slug, params.labelSlug)
}

export function useVisibleLabels(enabled = true) {
  return useQuery({
    queryKey: getVisibleLabelsQueryKey(),
    queryFn: getVisibleLabels,
    enabled,
  })
}

export function useSkillLabels(namespace: string, slug: string, enabled = true) {
  return useQuery({
    queryKey: getSkillLabelsQueryKey(namespace, slug),
    queryFn: () => getSkillLabels(namespace, slug),
    enabled: enabled && !!namespace && !!slug,
  })
}

export function useAdminLabelDefinitions(enabled = true) {
  return useQuery({
    queryKey: getAdminLabelDefinitionsQueryKey(),
    queryFn: getAdminLabelDefinitions,
    enabled,
  })
}

function invalidateSkillLabelQueries(queryClient: ReturnType<typeof useQueryClient>, namespace: string, slug: string) {
  queryClient.invalidateQueries({ queryKey: ['skills', namespace, slug] })
  queryClient.invalidateQueries({ queryKey: ['labels', 'skill', namespace, slug] })
  queryClient.invalidateQueries({ queryKey: ['skills'] })
}

export function useAttachSkillLabel() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: attachSkillLabel,
    onSuccess: (_data, variables) => {
      invalidateSkillLabelQueries(queryClient, variables.namespace, variables.slug)
    },
  })
}

export function useDetachSkillLabel() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: detachSkillLabel,
    onSuccess: (_data, variables) => {
      invalidateSkillLabelQueries(queryClient, variables.namespace, variables.slug)
    },
  })
}
