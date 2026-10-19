import { useState } from 'react'

export type BoardLayout = 'grid' | 'list'

export const LAYOUT_KEY = 'board.layout'

function readLayout(): BoardLayout {
  try {
    return window.localStorage.getItem(LAYOUT_KEY) === 'list' ? 'list' : 'grid'
  } catch {
    return 'grid'
  }
}

/** Grid or List board layout, remembered in localStorage (`board.layout`, default Grid). */
export function useBoardLayout(): [BoardLayout, (layout: BoardLayout) => void] {
  const [layout, setLayout] = useState<BoardLayout>(readLayout)
  const change = (next: BoardLayout) => {
    setLayout(next)
    try {
      window.localStorage.setItem(LAYOUT_KEY, next)
    } catch {
      // storage unavailable (private mode): keep it for this page only
    }
  }
  return [layout, change]
}
