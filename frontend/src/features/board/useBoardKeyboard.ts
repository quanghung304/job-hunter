import { useEffect, useLayoutEffect, useRef } from 'react'

export interface BoardKeyHandlers {
  next: () => void
  previous: () => void
  open: () => void
  shortlist: () => void
  dismiss: () => void
  applied: () => void
  posting: () => void
  focusSearch: () => void
}

function typing(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)
}

function dialogOpen(): boolean {
  return document.querySelector('[role="dialog"], [role="alertdialog"]') !== null
}

/**
 * Board shortcuts (frontend.md "Behavior › Board"): j/k, Enter, s, d, a, o, /. Ignored while
 * typing in a field, with a modifier key, or while a dialog is open. `Esc` in the search field
 * is handled by the field itself.
 */
export function useBoardKeyboard(handlers: BoardKeyHandlers, enabled = true) {
  const ref = useRef(handlers)
  useLayoutEffect(() => {
    ref.current = handlers
  })

  useEffect(() => {
    if (!enabled) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey) return
      if (typing(event.target) || dialogOpen()) return
      const h = ref.current
      const actions: Record<string, () => void> = {
        j: h.next,
        k: h.previous,
        Enter: h.open,
        s: h.shortlist,
        d: h.dismiss,
        a: h.applied,
        o: h.posting,
        '/': h.focusSearch,
      }
      const action = actions[event.key]
      if (!action) return
      // Enter on a focused button or link keeps its own meaning.
      if (event.key === 'Enter' && event.target instanceof HTMLElement && event.target.closest('button, a')) return
      event.preventDefault()
      action()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [enabled])
}
