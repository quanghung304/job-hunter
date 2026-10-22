import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense, useState } from 'react'
import { createBrowserRouter, RouterProvider, useLocation } from 'react-router'
import { AppHeader } from '@/components/AppHeader'
import { Toaster } from '@/components/ui/sonner'
import { useIsDesktop } from '@/lib/useMediaQuery'
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

/**
 * Providers + shell. Tests render <AppShell> inside their own router and client. The desktop
 * split view (`/jobs/:id`) fills the window without page scroll: its panels scroll on their own.
 */
export function AppShell() {
  const { pathname } = useLocation()
  const fixed = useIsDesktop() && pathname.startsWith('/jobs/')
  return (
    <div className={fixed ? 'flex h-dvh flex-col overflow-hidden' : undefined}>
      <AppHeader />
      <AppRoutes />
      <Toaster />
    </div>
  )
}

/**
 * A data router (needed for `useBlocker`, the Profile page's leave guard) with one catch-all route;
 * the pages keep their `<Routes>` in `routes.tsx`.
 */
const router = createBrowserRouter([{ path: '*', element: <AppShell /> }])

export default function App() {
  const [queryClient] = useState(createQueryClient)
  return (
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      {Devtools ? (
        <Suspense fallback={null}>
          <Devtools buttonPosition="bottom-right" />
        </Suspense>
      ) : null}
    </QueryClientProvider>
  )
}
