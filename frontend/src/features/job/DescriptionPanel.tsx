import { useState } from 'react'
import { cn } from 'cn'
import type { JobDetail } from '@/api/queries'
import { SectionCard } from '@/components/SectionCard'
import { SourceLinks } from '@/components/SourceLinks'
import { Button } from '@/components/ui/button'

/**
 * Card "Description": "Sources: via X" links, text collapsed to about 6 lines with a fade until
 * expanded. The parent keys it by job id, so the state resets per job and survives refetches.
 */
export function DescriptionPanel({ job }: { job: JobDetail }) {
  const [expanded, setExpanded] = useState(false)
  const long = job.description.length > 600 || job.description.split('\n').length > 6
  return (
    <SectionCard label="Description" actions={<SourceLinks postings={job.postings} />}>
      <div className="relative">
        <p
          className={cn(
            'm-0 max-w-[72ch] text-[15px] leading-[1.6] whitespace-pre-line text-ink-2',
            long && !expanded && 'max-h-[9.6em] overflow-hidden',
          )}
        >
          {job.description}
        </p>
        {long && !expanded ? (
          <div
            className="pointer-events-none absolute inset-x-0 bottom-0 h-12 bg-gradient-to-b from-transparent to-surface"
            aria-hidden
          />
        ) : null}
      </div>
      {long ? (
        <Button variant="link" className="mt-2" onClick={() => setExpanded((value) => !value)} aria-expanded={expanded}>
          {expanded ? 'Show less' : 'Show full description'}
        </Button>
      ) : null}
    </SectionCard>
  )
}
