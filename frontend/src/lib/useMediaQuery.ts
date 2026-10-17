import { useSyncExternalStore } from 'react'

/** Split view vs. mobile layout (frontend.md "Rules"). */
export const DESKTOP_QUERY = '(min-width: 1024px)'

/** Live result of a CSS media query; false where matchMedia is unavailable. */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      if (typeof window === 'undefined' || !window.matchMedia) return () => {}
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    () => (typeof window !== 'undefined' && window.matchMedia ? window.matchMedia(query).matches : false),
    () => false,
  )
}

export function useIsDesktop(): boolean {
  return useMediaQuery(DESKTOP_QUERY)
}
