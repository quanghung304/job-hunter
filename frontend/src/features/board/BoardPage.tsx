import { useParams } from 'react-router'
import { EmptyState } from '@/components/EmptyState'

/** Placeholder until T-020 (board and job detail). */
export function BoardPage() {
  const { id } = useParams()
  return (
    <main className="px-4 py-6 lg:px-6">
      <h1 className="sr-only">Board</h1>
      <EmptyState
        title={id ? `Job ${id}` : 'Board'}
        text={id ? 'The job detail arrives with T-020.' : 'The job board arrives with T-020.'}
      />
    </main>
  )
}
