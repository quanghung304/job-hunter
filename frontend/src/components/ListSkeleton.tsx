/** Loading rows: a 40×32 block and three bars; pulse is disabled by prefers-reduced-motion. */
export function ListSkeleton({ rows = 6 }: { rows?: number }) {
  return (
    <ul aria-busy="true" aria-label="Loading" className="m-0 list-none p-0">
      {Array.from({ length: rows }, (_, i) => (
        <li key={i} className="flex gap-3 border-b border-line-soft p-3.5">
          <span className="h-8 w-10 shrink-0 animate-pulse rounded-badge bg-neutral-fill" />
          <span className="flex flex-1 flex-col gap-2 pt-0.5">
            <span className="h-3 w-3/5 animate-pulse rounded-tag bg-neutral-fill" />
            <span className="h-2.5 w-2/5 animate-pulse rounded-tag bg-line-faint" />
            <span className="h-2.5 w-4/5 animate-pulse rounded-tag bg-line-faint" />
          </span>
        </li>
      ))}
    </ul>
  )
}
