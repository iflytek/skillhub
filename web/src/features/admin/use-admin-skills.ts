import { useMutation, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'

export function useRestoreHiddenSkill() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (skillId: number) => adminApi.unhideSkill(skillId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['skills'] })
    },
  })
}
