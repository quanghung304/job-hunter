import type { ReactNode } from 'react'
import { Star, X } from 'lucide-react'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { EligibilityTag } from '@/components/EligibilityTag'
import { EmptyState } from '@/components/EmptyState'
import { ErrorState } from '@/components/ErrorState'
import { FactChip } from '@/components/FactChip'
import { Kbd } from '@/components/Kbd'
import { ListSkeleton } from '@/components/ListSkeleton'
import { RuleResultTag } from '@/components/RuleResultTag'
import { ScoreBadge } from '@/components/ScoreBadge'
import { SectionCard } from '@/components/SectionCard'
import { StatusTag } from '@/components/StatusTag'
import type { Eligibility, TrackingStatus } from '@/lib/labels'
import { Button } from '@/components/ui/button'

const STATUSES: TrackingStatus[] = [
  'NEW',
  'SHORTLISTED',
  'APPLIED',
  'INTERVIEWING',
  'OFFER',
  'REJECTED',
  'WITHDRAWN',
  'DISMISSED',
]
const ELIGIBILITIES: Eligibility[] = ['ELIGIBLE', 'UNCERTAIN', 'INELIGIBLE']
const SCORES = [92, 85, 84, 70, 69, 50, 49, null]

/**
 * Dev-only route (/dev/components): every shared component in every variant, mirroring the
 * "Components & states" mockup artboard for visual review (T-019).
 */
export default function DevComponentsPage() {
  return (
    <main className="mx-auto flex max-w-[1280px] flex-col gap-5 px-4 py-7 lg:px-6">
      <h1 className="m-0 text-[22px] font-semibold tracking-[-0.01em]">Components &amp; states</h1>

      <SectionCard label="Score badge">
        <Row title="sm (rows)">
          {SCORES.map((s) => (
            <ScoreBadge key={String(s)} score={s} />
          ))}
        </Row>
        <Row title="lg (detail)">
          {[92, 76, 61, 38, null].map((s) => (
            <ScoreBadge key={String(s)} size="lg" score={s} />
          ))}
        </Row>
      </SectionCard>

      <SectionCard label="Tags">
        <Row title="Status">
          {STATUSES.map((s) => (
            <StatusTag key={s} status={s} />
          ))}
        </Row>
        <Row title="Eligibility">
          {ELIGIBILITIES.map((e) => (
            <EligibilityTag key={e} eligibility={e} />
          ))}
        </Row>
        <Row title="Rule result">
          <RuleResultTag result="OK" />
          <RuleResultTag result="UNKNOWN" />
          <RuleResultTag result="FAIL" />
        </Row>
        <Row title="Fact chips">
          <FactChip>Contract</FactChip>
          <FactChip>EU</FactChip>
          <FactChip>
            <span className="num">€45/h</span>
          </FactChip>
          <FactChip emphasis>Detail header</FactChip>
        </Row>
      </SectionCard>

      <SectionCard label="Buttons">
        <Row title="Variants">
          <Button variant="primary" shortcut="s">
            Shortlist
          </Button>
          <Button variant="secondary" shortcut="a">
            Mark applied
          </Button>
          <Button variant="quiet" shortcut="d">
            Dismiss
          </Button>
          <Button variant="leave-app">Apply ↗</Button>
          <Button variant="primary" disabled>
            Running…
          </Button>
          <Button variant="link">Reset</Button>
        </Row>
        <Row title="Icon buttons">
          <Button variant="quiet" size="icon" aria-label="Shortlist" title="Shortlist (s)">
            <Star className="size-4 fill-accent text-accent" />
          </Button>
          <Button variant="quiet" size="icon" aria-label="Dismiss" title="Dismiss (d)">
            <X className="size-4 text-muted" />
          </Button>
          <Button variant="secondary" size="icon-touch" aria-label="Dismiss (touch)">
            <X className="size-5" />
          </Button>
        </Row>
        <Row title="Toast">
          <Button
            variant="secondary"
            onClick={() =>
              toast('Dismissed "Senior Backend Engineer"', { action: { label: 'Undo', onClick: () => {} } })
            }
          >
            Show toast
          </Button>
        </Row>
      </SectionCard>

      <SectionCard label="Keyboard hints">
        <p className="num m-0 flex flex-wrap items-center gap-1.5 text-[12px] text-muted">
          <Kbd>j</Kbd>/<Kbd>k</Kbd> move · <Kbd>Enter</Kbd> open · <Kbd>s</Kbd> shortlist · <Kbd>d</Kbd> dismiss ·{' '}
          <Kbd>a</Kbd> applied · <Kbd>o</Kbd> posting · <Kbd>/</Kbd> search
        </p>
      </SectionCard>

      <div className="grid gap-5 [grid-template-columns:repeat(auto-fit,minmax(320px,1fr))]">
        <SectionCard label="Empty state">
          <EmptyState title="Inbox zero" text="No jobs match these filters." action={<Button>Reset filters</Button>} />
        </SectionCard>
        <SectionCard label="Loading">
          <ListSkeleton rows={3} />
        </SectionCard>
      </div>

      <div className="grid gap-5 [grid-template-columns:repeat(auto-fit,minmax(320px,1fr))]">
        <ErrorState
          title="Could not change status"
          error={
            new ApiError({
              status: 409,
              code: 'ILLEGAL_STATUS_TRANSITION',
              detail: 'Cannot change status from OFFER to NEW',
            })
          }
        />
        <ErrorState error={ApiError.network(new TypeError('Failed to fetch'))} />
      </div>
    </main>
  )
}

function Row({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2 border-b border-line-soft py-3 last:border-b-0 sm:flex-row sm:items-center">
      <span className="w-32 shrink-0 text-[13px] text-muted">{title}</span>
      <div className="flex flex-wrap items-center gap-2">{children}</div>
    </div>
  )
}
