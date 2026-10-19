import { Check, Minus, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import type { JobDetail } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { SectionLabel } from '@/components/SectionLabel'
import { applyAdviceLabel } from '@/lib/labels'

function Points({
  label,
  items,
  icon,
  empty,
  itemClass,
}: {
  label: string
  items: readonly string[]
  icon: ReactNode
  empty: string
  itemClass?: string
}) {
  return (
    <div>
      <SectionLabel as="h3" className="mb-2">
        {label}
      </SectionLabel>
      {items.length === 0 ? (
        <p className="m-0 text-[13.5px] text-muted">{empty}</p>
      ) : (
        <ul className="m-0 flex list-none flex-col gap-1.5 p-0">
          {items.map((item) => (
            <li key={item} className={`flex gap-2 text-[13.5px] text-ink-2 ${itemClass ?? ''}`}>
              <span className="mt-0.5 shrink-0">{icon}</span>
              <span>{item}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * Card "Why it fits": the LLM summary and advice (in the profile's language), then strengths,
 * gaps and red flags. Before scoring it says what to do next.
 */
export function WhyItFits({ job, language }: { job: JobDetail; language: string | undefined }) {
  if (job.score === null) {
    return (
      <SectionCard label="Why it fits">
        <p className="m-0 text-[13.5px] text-muted">
          {job.analysisStage === 'NOT_ANALYZED'
            ? 'Not analyzed yet. Analyze reads the posting (one LLM call), then Score rates the fit.'
            : job.eligibility === 'INELIGIBLE'
              ? 'Not eligible for your profile, so it is not scored.'
              : 'Not scored yet. Score rates the fit with one LLM call.'}
        </p>
      </SectionCard>
    )
  }
  return (
    <SectionCard label="Why it fits">
      {job.summary ? (
        <p lang={language} className="m-0 text-[15.5px] leading-normal text-ink lg:text-[16px]">
          {job.summary}
        </p>
      ) : null}
      {job.applyAdvice ? (
        <p lang={language} className="mt-2 mb-0 text-[13.5px] text-ink-2">
          <span className="font-semibold">{applyAdviceLabel(language)}</span> {job.applyAdvice}
        </p>
      ) : null}
      <div className="mt-4 grid grid-cols-[repeat(auto-fit,minmax(200px,1fr))] gap-5">
        <Points
          label="Strengths"
          items={job.strengths}
          empty="None listed"
          icon={<Check className="size-4 text-accent" strokeWidth={2} aria-hidden />}
        />
        <Points
          label="Gaps"
          items={job.gaps}
          empty="None listed"
          icon={<Minus className="size-4 text-muted" strokeWidth={2} aria-hidden />}
        />
        <Points
          label="Red flags"
          items={job.redFlags}
          empty="None found"
          itemClass="!text-attention-fg"
          icon={<TriangleAlert className="size-4 text-attention-icon" strokeWidth={1.8} aria-hidden />}
        />
      </div>
    </SectionCard>
  )
}
