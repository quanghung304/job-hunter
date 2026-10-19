import { ChevronLeft, ChevronRight } from 'lucide-react'
import { cn } from 'cn'
import { Button } from '@/components/ui/button'
import { pageItems } from './pageItems'

/**
 * Grid pagination: mono "Showing 13–24 of 30 jobs", previous, numbered pages (current one in ink,
 * `aria-current="page"`), next; previous/next are disabled at the ends. `page` is 1-based.
 */
export function Pagination({
  page,
  pageSize,
  totalItems,
  onChange,
}: {
  page: number
  pageSize: number
  totalItems: number
  onChange: (page: number) => void
}) {
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize))
  const first = totalItems === 0 ? 0 : (page - 1) * pageSize + 1
  const last = Math.min(totalItems, page * pageSize)
  return (
    <nav aria-label="Pages" className="flex flex-wrap items-center gap-x-4 gap-y-2">
      <p className="num m-0 text-[12.5px] text-muted" aria-live="polite">
        Showing {first}–{last} of {totalItems} {totalItems === 1 ? 'job' : 'jobs'}
      </p>
      <span className="flex-1" />
      <ul className="m-0 flex list-none items-center gap-1 p-0">
        <li>
          <Button
            variant="secondary"
            size="icon"
            className="max-lg:size-11"
            aria-label="Previous page"
            disabled={page <= 1}
            onClick={() => onChange(page - 1)}
          >
            <ChevronLeft />
          </Button>
        </li>
        {pageItems(page, totalPages).map((item, i) =>
          item === 'gap' ? (
            <li key={`gap-${i}`} className="num px-1 text-[13px] text-muted" aria-hidden>
              …
            </li>
          ) : (
            <li key={item}>
              <button
                type="button"
                aria-label={`Page ${item}`}
                aria-current={item === page ? 'page' : undefined}
                onClick={() => {
                  if (item !== page) onChange(item)
                }}
                className={cn(
                  'num inline-flex h-8 min-w-8 items-center justify-center rounded-control px-2 text-[13px] max-lg:h-11 max-lg:min-w-11',
                  item === page
                    ? 'bg-ink font-semibold text-white'
                    : 'border border-control-border bg-surface text-ink-2 hover:bg-surface-muted',
                )}
              >
                {item}
              </button>
            </li>
          ),
        )}
        <li>
          <Button
            variant="secondary"
            size="icon"
            className="max-lg:size-11"
            aria-label="Next page"
            disabled={page >= totalPages}
            onClick={() => onChange(page + 1)}
          >
            <ChevronRight />
          </Button>
        </li>
      </ul>
    </nav>
  )
}
