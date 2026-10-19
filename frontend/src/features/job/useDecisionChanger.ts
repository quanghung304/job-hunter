import { useCallback, useLayoutEffect, useRef } from 'react'
import { toast } from 'sonner'
import { useSetDecision } from '@/api/queries'
import { type Decision, decisionChangedText } from '@/lib/labels'
import { errorText } from './useStatusChanger'

/**
 * Sets the owner's eligibility decision optimistically and shows the toast: always with "Undo"
 * (any decision may change to any other, data-model §2.1), the server's message when it fails.
 */
export function useDecisionChanger() {
  const mutation = useSetDecision()
  const mutate = mutation.mutate
  const changeRef = useRef<(id: number, from: Decision | null, to: Decision | null, undoable?: boolean) => void>(
    () => {},
  )

  const change = useCallback(
    (id: number, from: Decision | null, to: Decision | null, undoable = true) => {
      if (from === to) return
      toast(decisionChangedText(to), {
        id: 'decision-change',
        action: undoable ? { label: 'Undo', onClick: () => changeRef.current(id, to, from, false) } : undefined,
      })
      mutate(
        { id, to },
        {
          onError: (error) => {
            toast.error(`Could not change the decision: ${errorText(error)}`, { id: 'decision-change' })
          },
        },
      )
    },
    [mutate],
  )
  useLayoutEffect(() => {
    changeRef.current = change
  })

  return { change }
}

/** `e` / `n`: mark eligible / not eligible; pressing the key of the current decision clears it. */
export function toggledDecision(current: Decision | null, key: Decision): Decision | null {
  return current === key ? null : key
}
