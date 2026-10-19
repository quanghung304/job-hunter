import { ChevronDown } from 'lucide-react'
import { Popover } from 'radix-ui'
import type { ReactNode, Ref } from 'react'
import { Button } from '@/components/ui/button'
import { ExpiredToggle, MultiCheck, PostedOptions, ScoreRange } from './FilterControls'
import {
  LIST_FILTER_LABELS,
  type ListFilter,
  listValueText,
  postedValueText,
  scoreValueText,
  useFilterOptions,
} from './filterOptions'
import { SearchField } from './SearchField'
import type { BoardFilters } from './useBoardFilters'

export type Density = 'comfortable' | 'compact'

/** A filter dropdown button: "Label **value**". */
function FilterMenu({ label, value, children }: { label: string; value: string; children: ReactNode }) {
  const active = value !== 'Any' && value !== 'Any time'
  return (
    <Popover.Root>
      <Popover.Trigger asChild>
        <Button variant="secondary" size="default" aria-label={`${label}: ${value}`}>
          <span className="text-muted">{label}</span>
          <span className={active ? 'font-semibold text-ink' : 'text-ink-2'}>{value}</span>
          <ChevronDown className="size-3.5 text-muted" aria-hidden />
        </Button>
      </Popover.Trigger>
      <Popover.Portal>
        <Popover.Content
          align="start"
          sideOffset={6}
          className="z-50 min-w-52 rounded-card border border-line bg-surface p-1.5 shadow-toast"
        >
          {children}
        </Popover.Content>
      </Popover.Portal>
    </Popover.Root>
  )
}

const LIST_FILTERS: ListFilter[] = ['status', 'analysisStage', 'decision', 'checks']
const LATE_LIST_FILTERS: ListFilter[] = ['source', 'employmentType']

/**
 * Desktop filter bar: search, one dropdown per filter, Reset; right: job count, sort and
 * density (frontend.md "Board").
 */
export function FilterBar({
  filters,
  update,
  reset,
  activeCount,
  total,
  density,
  onDensityChange,
  searchRef,
}: {
  filters: BoardFilters
  update: (patch: Partial<BoardFilters>) => void
  reset: () => void
  activeCount: number
  total: number | undefined
  density: Density
  onDensityChange: (density: Density) => void
  searchRef: Ref<HTMLInputElement>
}) {
  const options = useFilterOptions()
  const listMenu = (name: ListFilter) => (
    <FilterMenu key={name} label={LIST_FILTER_LABELS[name]} value={listValueText(filters[name], options[name])}>
      <MultiCheck
        legend={LIST_FILTER_LABELS[name]}
        options={options[name]}
        selected={filters[name] as string[]}
        onChange={(values) => update({ [name]: values } as Partial<BoardFilters>)}
      />
    </FilterMenu>
  )

  return (
    <div
      role="search"
      className="flex flex-wrap items-center gap-2 border-b border-line bg-surface-muted px-6 py-3"
      aria-label="Board filters"
    >
      <SearchField value={filters.q} onChange={(q) => update({ q })} inputRef={searchRef} className="w-64" />
      {LIST_FILTERS.map(listMenu)}
      <FilterMenu label="Score" value={scoreValueText(filters)}>
        <ScoreRange filters={filters} onChange={update} />
      </FilterMenu>
      {LATE_LIST_FILTERS.map(listMenu)}
      <FilterMenu label="Posted" value={postedValueText(filters)}>
        <PostedOptions value={filters.postedWithinDays} onChange={(postedWithinDays) => update({ postedWithinDays })} />
        <div className="mt-1 border-t border-line-soft pt-1">
          <ExpiredToggle checked={filters.includeExpired} onChange={(includeExpired) => update({ includeExpired })} />
        </div>
      </FilterMenu>
      {activeCount > 0 ? (
        <Button variant="link" size="sm" onClick={reset}>
          Reset
        </Button>
      ) : null}
      <div className="ml-auto flex items-center gap-2">
        <span className="num text-[12.5px] text-muted" aria-live="polite">
          {total === undefined ? '' : `${total} ${total === 1 ? 'job' : 'jobs'}`}
        </span>
        <Button
          variant="secondary"
          onClick={() => update({ sort: filters.sort === 'score' ? 'posted' : 'score' })}
          aria-label={`Sort by ${filters.sort === 'score' ? 'score' : 'posting date'}; change`}
        >
          <span className="text-muted">Sort:</span> {filters.sort === 'score' ? 'Score' : 'Posted'}
        </Button>
        <Button
          variant="quiet"
          onClick={() => onDensityChange(density === 'comfortable' ? 'compact' : 'comfortable')}
          aria-pressed={density === 'compact'}
        >
          {density === 'comfortable' ? 'Comfortable' : 'Compact'}
        </Button>
      </div>
    </div>
  )
}
