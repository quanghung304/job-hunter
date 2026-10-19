import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react'
import { useUpdateNotes } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { Textarea } from '@/components/ui/textarea'
import { errorText } from './useStatusChanger'

export const NOTES_DELAY_MS = 1000
const MAX_NOTES = 10_000

type SaveState = 'idle' | 'pending' | 'saving' | 'saved' | 'error'

/**
 * Notes autosave 1 s after typing stops. The parent keys it by job id; a pending save is sent
 * when the editor goes away (another job selected).
 */
export function NotesEditor({ jobId, initial }: { jobId: number; initial: string | null }) {
  const id = useId()
  const [text, setText] = useState(initial ?? '')
  const [state, setState] = useState<SaveState>('idle')
  const [error, setError] = useState<string | null>(null)
  const mutation = useUpdateNotes(jobId)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const pending = useRef<string | null>(null)
  const save = useRef<(value: string) => void>(() => {})

  const mutate = mutation.mutate
  useLayoutEffect(() => {
    save.current = (value: string) => {
      pending.current = null
      setState('saving')
      mutate(value, {
        onSuccess: () => {
          setState(pending.current === null ? 'saved' : 'pending')
          setError(null)
        },
        onError: (e) => {
          setState('error')
          setError(errorText(e))
        },
      })
    }
  })

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current)
      if (pending.current !== null) save.current(pending.current)
    },
    [],
  )

  const onChange = (value: string) => {
    setText(value)
    setState('pending')
    pending.current = value
    if (timer.current) clearTimeout(timer.current)
    timer.current = setTimeout(() => {
      timer.current = null
      if (pending.current !== null) save.current(pending.current)
    }, NOTES_DELAY_MS)
  }

  const hint =
    state === 'saving'
      ? 'Saving…'
      : state === 'saved'
        ? 'Saved · just now'
        : state === 'error'
          ? `Not saved: ${error ?? ''}`
          : 'Autosaves as you type'

  return (
    <SectionCard label={<label htmlFor={id}>Notes</label>}>
      <Textarea
        id={id}
        rows={5}
        maxLength={MAX_NOTES}
        value={text}
        onChange={(event) => onChange(event.target.value)}
        className="field-sizing-fixed min-h-[7.5rem] bg-surface text-[14px] md:text-[14px]"
        placeholder="Recruiter, salary talk, next steps…"
      />
      <p
        className={`num mt-2 mb-0 text-[12px] ${state === 'error' ? 'text-attention-fg' : 'text-muted'}`}
        role="status"
        aria-live="polite"
      >
        {hint}
      </p>
    </SectionCard>
  )
}
