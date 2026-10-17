import { Link } from 'react-router'
import { EmptyState } from '@/components/EmptyState'
import { Button } from '@/components/ui/button'

export function NotFoundPage() {
  return (
    <main className="px-4 py-6 lg:px-6">
      <h1 className="sr-only">Page not found</h1>
      <EmptyState
        title="Page not found"
        text="This address does not exist."
        action={
          <Button asChild variant="secondary">
            <Link to="/">Back to the board</Link>
          </Button>
        }
      />
    </main>
  )
}
