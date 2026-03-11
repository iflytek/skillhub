import { useMutation } from '@tanstack/react-query'
import type { PublishResult } from '@/api/types'

function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

interface PublishSkillParams {
  namespace: string
  file: File
}

export function usePublishSkill() {
  return useMutation({
    mutationFn: async ({ namespace, file }: PublishSkillParams) => {
      const formData = new FormData()
      formData.append('file', file)

      const csrfToken = getCsrfToken()
      const headers: HeadersInit = {
        ...(csrfToken && { 'X-XSRF-TOKEN': csrfToken }),
      }

      const res = await fetch(`/api/v1/skills/${namespace}/publish`, {
        method: 'POST',
        headers,
        body: formData,
      })

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}`)
      }

      const json = await res.json()
      return json as PublishResult
    },
  })
}
