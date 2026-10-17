import { EmptyState } from '@/components/EmptyState'

/** Placeholder until T-021 (admin page). */
export function AdminPage() {
  return (
    <main className="mx-auto max-w-[1280px] px-4 py-7 lg:px-6">
      <h1 className="m-0 text-[22px] font-semibold tracking-[-0.01em]">System</h1>
      <EmptyState title="Admin" text="Pipeline, sources and LLM usage arrive with T-021." />
    </main>
  )
}
