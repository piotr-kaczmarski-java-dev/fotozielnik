import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiSend } from '../../api/client.ts'
import { keys } from '../../api/queries.ts'
import type { TrashResponse } from '../../api/types.ts'

/** Move to / restore from the trash (FR-022); refreshes the grid, the trash and its counter. */
export function useTrashMutation(action: 'trash' | 'restore') {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (ids: string[]) => apiSend<TrashResponse>('POST', `/api/photos/${action}`, { ids }),
    onSuccess: (_r, ids) => {
      void client.invalidateQueries({ queryKey: keys.photos })
      void client.invalidateQueries({ queryKey: keys.trash })
      ids.forEach((id) => void client.invalidateQueries({ queryKey: keys.photo(id) }))
    },
  })
}
