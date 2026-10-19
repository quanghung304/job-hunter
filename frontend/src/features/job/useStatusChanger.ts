import { useCallback, useLayoutEffect, useRef } from 'react'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { useChangeStatus } from '@/api/queries'
import { statusChangedText, type TrackingStatus } from '@/lib/labels'
import { canTransition } from '@/lib/statusMachine'

export function errorText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.network) return 'Backend not reachable — is the app running?'
    return error.detail ?? error.message
  }
  return error instanceof Error ? error.message : 'Request failed'
}

/**
 * Changes a job's status optimistically and shows the toast: "Undo" when the state machine allows
 * the way back (data-model §2), the server's message when the change fails (rolled back).
 */
export function useStatusChanger() {
  const mutation = useChangeStatus()
  const mutate = mutation.mutate
  const changeRef = useRef<(id: number, from: TrackingStatus, to: TrackingStatus, undoable?: boolean) => void>(() => {})

  const change = useCallback(
    (id: number, from: TrackingStatus, to: TrackingStatus, undoable = true) => {
      const undo = undoable && canTransition(to, from)
      toast(statusChangedText(from, to), {
        id: 'status-change',
        action: undo ? { label: 'Undo', onClick: () => changeRef.current(id, to, from, false) } : undefined,
      })
      mutate(
        { id, from, to },
        {
          onError: (error) => {
            toast.error(`Could not change the status: ${errorText(error)}`, { id: 'status-change' })
          },
        },
      )
    },
    [mutate],
  )
  useLayoutEffect(() => {
    changeRef.current = change
  })

  return { change, pending: mutation.isPending }
}
