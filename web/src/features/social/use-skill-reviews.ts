import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchJson, getCsrfHeaders, WEB_API_PREFIX } from '@/api/client'
import type { components } from '@/api/generated/schema'

type GeneratedSkillReview = components['schemas']['SkillReviewResponse']
type GeneratedMySkillReview = components['schemas']['SkillReviewMeResponse']
type GeneratedSkillReviewPage = components['schemas']['PageResponseSkillReviewResponse']
type GeneratedReviewInput = components['schemas']['SkillReviewRequest']

export interface SkillReview extends Omit<GeneratedSkillReview,
  'id' | 'userId' | 'displayName' | 'avatarUrl' | 'score' | 'reviewText' | 'status' |
  'authoredByViewer' | 'moderationReason' | 'createdAt' | 'updatedAt'> {
  id: number
  userId?: string | null
  displayName: string
  avatarUrl?: string | null
  score: number
  reviewText: string
  status: 'VISIBLE' | 'HIDDEN'
  authoredByViewer: boolean
  moderationReason?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

export interface MySkillReview extends Omit<GeneratedMySkillReview,
  'rated' | 'score' | 'reviewed' | 'reviewId' | 'reviewText' | 'status' |
  'moderationReason' | 'createdAt' | 'updatedAt'> {
  rated: boolean
  score: number
  reviewed: boolean
  reviewId?: number | null
  reviewText?: string | null
  status?: 'VISIBLE' | 'HIDDEN' | null
  moderationReason?: string | null
  createdAt?: string | null
  updatedAt?: string | null
}

interface SkillReviewPage extends Omit<GeneratedSkillReviewPage, 'items' | 'total' | 'page' | 'size'> {
  items: SkillReview[]
  total: number
  page: number
  size: number
}

interface ReviewInput extends Omit<GeneratedReviewInput, 'score' | 'reviewText'> {
  score: number
  reviewText: string
}

async function listReviews(skillId: number, page: number): Promise<SkillReviewPage> {
  return fetchJson<SkillReviewPage>(`${WEB_API_PREFIX}/skills/${skillId}/reviews?page=${page}&size=20`)
}

async function getMyReview(skillId: number): Promise<MySkillReview> {
  return fetchJson<MySkillReview>(`${WEB_API_PREFIX}/skills/${skillId}/reviews/me`)
}

async function upsertReview(skillId: number, input: ReviewInput): Promise<MySkillReview> {
  return fetchJson<MySkillReview>(`${WEB_API_PREFIX}/skills/${skillId}/reviews/me`, {
    method: 'PUT',
    headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(input),
  })
}

async function clearReview(skillId: number): Promise<MySkillReview> {
  return fetchJson<MySkillReview>(`${WEB_API_PREFIX}/skills/${skillId}/reviews/me`, {
    method: 'DELETE',
    headers: getCsrfHeaders(),
  })
}

async function moderateReview(reviewId: number, action: 'hide' | 'restore'): Promise<SkillReview> {
  return fetchJson<SkillReview>(`/api/v1/admin/skill-reviews/${reviewId}/${action}`, {
    method: 'POST',
    headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
    body: action === 'hide' ? JSON.stringify({}) : undefined,
  })
}

export function useSkillReviews(skillId: number, page: number) {
  return useQuery({
    queryKey: ['skills', skillId, 'reviews', page],
    queryFn: () => listReviews(skillId, page),
    enabled: skillId > 0,
  })
}

export function useMySkillReview(skillId: number, enabled: boolean) {
  return useQuery({
    queryKey: ['skills', skillId, 'reviews', 'me'],
    queryFn: () => getMyReview(skillId),
    enabled: enabled && skillId > 0,
  })
}

function useReviewMutationInvalidation(skillId: number) {
  const queryClient = useQueryClient()
  return () => {
    queryClient.invalidateQueries({ queryKey: ['skills', skillId, 'reviews'] })
    queryClient.invalidateQueries({ queryKey: ['skills', skillId, 'rating'] })
    queryClient.invalidateQueries({ queryKey: ['skills'] })
  }
}

export function useUpsertSkillReview(skillId: number) {
  const invalidate = useReviewMutationInvalidation(skillId)
  return useMutation({
    mutationFn: (input: ReviewInput) => upsertReview(skillId, input),
    onSuccess: invalidate,
  })
}

export function useClearSkillReview(skillId: number) {
  const invalidate = useReviewMutationInvalidation(skillId)
  return useMutation({
    mutationFn: () => clearReview(skillId),
    onSuccess: invalidate,
  })
}

export function useModerateSkillReview(skillId: number) {
  const invalidate = useReviewMutationInvalidation(skillId)
  return useMutation({
    mutationFn: ({ reviewId, action }: { reviewId: number; action: 'hide' | 'restore' }) =>
      moderateReview(reviewId, action),
    onSuccess: invalidate,
  })
}
