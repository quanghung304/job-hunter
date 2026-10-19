import { Fragment } from 'react'

/** "via Himalayas, Jobicy": each source opens the original posting in a new tab. */
export function SourceLinks({
  postings,
}: {
  postings: readonly { sourceCode: string; sourceName: string; url: string }[]
}) {
  if (postings.length === 0) return null
  return (
    <span className="text-[13px] text-muted">
      via{' '}
      {postings.map((posting, i) => (
        <Fragment key={`${posting.sourceCode}-${posting.url}`}>
          {i > 0 ? ', ' : null}
          <a
            href={posting.url}
            target="_blank"
            rel="noopener noreferrer"
            className="text-accent-deep underline underline-offset-2 hover:text-accent"
          >
            {posting.sourceName}
          </a>
        </Fragment>
      ))}
    </span>
  )
}
