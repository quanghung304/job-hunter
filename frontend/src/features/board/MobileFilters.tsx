import { Search, X } from 'lucide-react'
import { type Ref, useState } from 'react'
import { SectionLabel } from '@/components/SectionLabel'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { STATUS_LABELS } from '@/lib/labels'
import { ExpiredToggle, MultiCheck, PostedOptions, ScoreRange } from './FilterControls'
import { LIST_FILTER_LABELS, type ListFilter, postedValueText, scoreValueText, useFilterOptions } from './filterOptions'
import { SearchField } from './SearchField'
import type { BoardFilters } from './useBoardFilters'

const LISTS: ListFilter[] = ['status', 'analysisStage', 'decision', 'checks', 'source', 'employmentType']

/**
 * Mobile filter row: "Filters · N" pill (bottom sheet with every filter), pills of the active
 * values, a search toggle and the job count.
 */
export function MobileFilters({
  filters,
  update,
  reset,
  activeCount,
  total,
  searchRef,
}: {
  filters: BoardFilters
  update: (patch: Partial<BoardFilters>) => void
  reset: () => void
  activeCount: number
  total: number | undefined
  searchRef: Ref<HTMLInputElement>
}) {
  const options = useFilterOptions()
  const [open, setOpen] = useState(false)
  const [searching, setSearching] = useState(filters.q !== '')

  const pills: { key: string; text: string; clear: () => void }[] = []
  for (const name of LISTS) {
    for (const value of filters[name] as string[]) {
      const label =
        name === 'status'
          ? STATUS_LABELS[value as keyof typeof STATUS_LABELS]
          : (options[name].find((o) => o.value === value)?.label ?? value)
      pills.push({
        key: `${name}-${value}`,
        text: label,
        clear: () =>
          update({ [name]: (filters[name] as string[]).filter((v) => v !== value) } as Partial<BoardFilters>),
      })
    }
  }
  if (filters.minScore !== null || filters.maxScore !== null) {
    pills.push({
      key: 'score',
      text: `Score ${scoreValueText(filters)}`,
      clear: () => update({ minScore: null, maxScore: null }),
    })
  }
  if (filters.postedWithinDays !== null) {
    pills.push({ key: 'posted', text: postedValueText(filters), clear: () => update({ postedWithinDays: null }) })
  }
  if (filters.includeExpired) {
    pills.push({ key: 'expired', text: 'Incl. expired', clear: () => update({ includeExpired: false }) })
  }

  return (
    <div className="border-b border-line bg-surface-muted">
      <div className="flex items-center gap-2 px-4 pt-2">
        <span className="num text-[12.5px] text-muted">{total === undefined ? '' : `${total} jobs`}</span>
        <Button
          variant="quiet"
          size="icon-touch"
          className="ml-auto"
          aria-label={searching ? 'Close search' : 'Search'}
          aria-expanded={searching}
          onClick={() => {
            if (searching && filters.q !== '') update({ q: '' })
            setSearching((value) => !value)
          }}
        >
          {searching ? <X className="size-5" /> : <Search className="size-5" />}
        </Button>
      </div>
      {searching ? (
        <div className="px-4 pb-2">
          <SearchField
            value={filters.q}
            onChange={(q) => update({ q })}
            inputRef={searchRef}
            autoFocus
            className="w-full"
          />
        </div>
      ) : null}
      <div className="flex gap-2 overflow-x-auto px-4 pb-3" role="toolbar" aria-label="Filters">
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="inline-flex min-h-11 shrink-0 items-center rounded-full bg-ink px-4 text-[13.5px] font-medium text-white"
        >
          Filters · <span className="num ml-1">{activeCount}</span>
        </button>
        {pills.map((pill) => (
          <button
            key={pill.key}
            type="button"
            onClick={pill.clear}
            aria-label={`Remove filter ${pill.text}`}
            className="inline-flex min-h-11 shrink-0 items-center gap-1.5 rounded-full border border-control-border bg-surface px-3.5 text-[13.5px] text-ink-2"
          >
            {pill.text}
            <X className="size-3.5 text-muted" aria-hidden />
          </button>
        ))}
      </div>
      <Sheet open={open} onOpenChange={setOpen}>
        <SheetContent side="bottom" className="max-h-[85vh] overflow-y-auto rounded-t-dialog bg-surface">
          <SheetHeader>
            <SheetTitle>Filters</SheetTitle>
          </SheetHeader>
          <div className="flex flex-col gap-4 px-4 pb-4">
            {LISTS.map((name) => (
              <section key={name}>
                <SectionLabel as="h3" className="mb-1">
                  {LIST_FILTER_LABELS[name]}
                </SectionLabel>
                <MultiCheck
                  legend={LIST_FILTER_LABELS[name]}
                  options={options[name]}
                  selected={filters[name] as string[]}
                  onChange={(values) => update({ [name]: values } as Partial<BoardFilters>)}
                />
              </section>
            ))}
            <section>
              <SectionLabel as="h3" className="mb-1">
                Score
              </SectionLabel>
              <ScoreRange filters={filters} onChange={update} />
            </section>
            <section>
              <SectionLabel as="h3" className="mb-1">
                Posted
              </SectionLabel>
              <PostedOptions
                value={filters.postedWithinDays}
                onChange={(postedWithinDays) => update({ postedWithinDays })}
              />
              <ExpiredToggle
                checked={filters.includeExpired}
                onChange={(includeExpired) => update({ includeExpired })}
              />
            </section>
            <section className="flex items-center gap-3">
              <SectionLabel as="h3">Sort</SectionLabel>
              <Button
                variant="secondary"
                size="lg"
                onClick={() => update({ sort: filters.sort === 'score' ? 'posted' : 'score' })}
              >
                {filters.sort === 'score' ? 'Score' : 'Posted'}
              </Button>
            </section>
            <div className="flex gap-2">
              <Button variant="secondary" size="lg" className="flex-1" onClick={reset} disabled={activeCount === 0}>
                Reset
              </Button>
              <Button variant="primary" size="lg" className="flex-1" onClick={() => setOpen(false)}>
                Show jobs
              </Button>
            </div>
          </div>
        </SheetContent>
      </Sheet>
    </div>
  )
}
