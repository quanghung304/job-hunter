/** Page numbers to show: all up to 7 pages, else first, last and the neighbours of the current one with gaps. */
export function pageItems(current: number, total: number): (number | 'gap')[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1)
  const pages = new Set([1, total, current - 1, current, current + 1])
  if (current <= 3) [2, 3, 4].forEach((p) => pages.add(p))
  if (current >= total - 2) [total - 3, total - 2, total - 1].forEach((p) => pages.add(p))
  const sorted = [...pages].filter((p) => p >= 1 && p <= total).sort((a, b) => a - b)
  const items: (number | 'gap')[] = []
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) items.push('gap')
    items.push(p)
  })
  return items
}
