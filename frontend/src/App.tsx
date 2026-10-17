import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense, useState } from 'react'
import { BrowserRouter } from 'react-router'
import { AppHeader } from '@/components/AppHeader'
import { Toaster } from '@/components/ui/sonner'
import { AppRoutes } from '@/routes'

const Devtools = import.meta.env.DEV
  ? lazy(() => import('@tanstack/react-query-devtools').then((m) => ({ default: m.ReactQueryDevtools })))
  : null

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: 1, refetchOnWindowFocus: false },
    },
  })
}

/** Providers + shell. Tests render <AppShell> inside their own router and client. */
export function AppShell() {
  return (
    <>
      <AppHeader />
      <AppRoutes />
      <Toaster />
    </>
  )
}

export default function App() {
  const [queryClient] = useState(createQueryClient)
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppShell />
      </BrowserRouter>
      {Devtools ? (
        <Suspense fallback={null}>
          <Devtools buttonPosition="bottom-right" />
        </Suspense>
      ) : null}
    </QueryClientProvider>
  )
}
