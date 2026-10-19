import { useParams } from 'react-router'
import { JobDetailPage } from '@/features/job/JobDetailPage'
import { JobSplitView } from '@/features/job/JobSplitView'
import { useIsDesktop } from '@/lib/useMediaQuery'
import { BoardGrid } from './BoardGrid'

/**
 * `/` and `/jobs/:id`. `/` is the board grid (mobile: one column of cards). `/jobs/:id` is the
 * split view on desktop (list + detail) and the full-page detail on mobile.
 */
export function BoardPage() {
  const params = useParams()
  const routeId = params.id !== undefined && /^\d+$/.test(params.id) ? Number(params.id) : null
  const isDesktop = useIsDesktop()
  if (routeId === null) return <BoardGrid isDesktop={isDesktop} />
  // Not keyed by id: selecting another job keeps the list panel (and its scroll) mounted.
  return isDesktop ? <JobSplitView id={routeId} /> : <JobDetailPage key={routeId} id={routeId} />
}
