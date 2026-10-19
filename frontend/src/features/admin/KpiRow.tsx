import type { AdminOverview } from '@/api/queries'
import { SectionLabel } from '@/components/SectionLabel'

function Kpi({ label, value, sub }: { label: string; value: number; sub: string }) {
  return (
    <div className="rounded-card border border-line bg-surface px-5 py-4">
      <SectionLabel as="h3">{label}</SectionLabel>
      <p className="num mt-1.5 mb-0 text-[26px] leading-tight font-semibold text-ink">{value}</p>
      <p className="mt-0.5 mb-0 text-[13px] text-muted">{sub}</p>
    </div>
  )
}

/** Four KPI cards: active jobs, new today, scored today, to review. */
export function KpiRow({ kpis, callsToday }: { kpis: AdminOverview['kpis']; callsToday: number }) {
  return (
    <section aria-label="Key figures" className="grid grid-cols-[repeat(auto-fit,minmax(220px,1fr))] gap-4">
      <Kpi
        label="Active jobs"
        value={kpis.activeJobs}
        sub={`${kpis.sourceCount} sources · ${kpis.companyBoardCount} company boards`}
      />
      <Kpi label="New today" value={kpis.newToday} sub={`${kpis.prefilterRejectedToday} rejected by the prefilter`} />
      <Kpi label="Scored today" value={kpis.scoredToday} sub={`${callsToday} LLM calls today`} />
      <Kpi label="To review" value={kpis.toReview} sub="In the board's default view" />
    </section>
  )
}
