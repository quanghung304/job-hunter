import { ChevronDown } from 'lucide-react'
import { Popover } from 'radix-ui'
import type { ReactNode, Ref } from 'react'
import { Button } from '@/components/ui/button'
import type { BoardLayout } from './boardLayout'
import { LayoutToggle } from './LayoutToggle'
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

/** A filter dropdown button: "Label **value**". */
function FilterMenu({
  label,
  value,
  active = value !== 'Any' && value !== 'Any time',
  children,
}: {
  label: string
  value: string
  active?: boolean
  children: ReactNode
}) {
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
 * Desktop filter bar: search, one dropdown per filter (Status, Analysis, Decision, Checks, Score,
 * Source, Type, Posted), Reset; right: Grid/List toggle and sort (frontend.md "Board grid").
 */
export function FilterBar({
  filters,
  update,
  reset,
  activeCount,
  layout,
  onLayoutChange,
  searchRef,
}: {
  filters: BoardFilters
  update: (patch: Partial<BoardFilters>) => void
  reset: () => void
  activeCount: number
  layout: BoardLayout
  onLayoutChange: (layout: BoardLayout) => void
  searchRef: Ref<HTMLInputElement>
}) {
  const options = useFilterOptions()
  // With no filter at all the default view applies (api-spec `GET /api/jobs`).
  const defaults: Partial<Record<ListFilter, string>> =
    activeCount === 0 ? { status: 'All but dismissed', decision: 'Not marked ineligible' } : {}
  const listMenu = (name: ListFilter) => (
    <FilterMenu
      key={name}
      label={LIST_FILTER_LABELS[name]}
      value={defaults[name] ?? listValueText(filters[name], options[name])}
      active={filters[name].length > 0}
    >
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
        <LayoutToggle value={layout} onChange={onLayoutChange} />
        <Button
          variant="secondary"
          onClick={() => update({ sort: filters.sort === 'score' ? 'posted' : 'score' })}
          aria-label={`Sort by ${filters.sort === 'score' ? 'score' : 'posting date'}; change`}
        >
          <span className="text-muted">Sort:</span> {filters.sort === 'score' ? 'Score' : 'Posted'}
        </Button>
      </div>
    </div>
  )
}
