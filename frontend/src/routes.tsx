import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router'
import { BoardPage } from '@/features/board/BoardPage'
import { AdminPage } from '@/features/admin/AdminPage'
import { NotFoundPage } from '@/features/NotFoundPage'
import { ProfilePage } from '@/features/profile/ProfilePage'

/** Dev-only visual review page; excluded from production builds. */
const DevComponentsPage = import.meta.env.DEV ? lazy(() => import('@/features/dev/DevComponentsPage')) : null

export function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<BoardPage />} />
      <Route path="/jobs/:id" element={<BoardPage />} />
      <Route path="/admin" element={<AdminPage />} />
      <Route path="/profile" element={<ProfilePage />} />
      {DevComponentsPage ? (
        <Route
          path="/dev/components"
          element={
            <Suspense fallback={null}>
              <DevComponentsPage />
            </Suspense>
          }
        />
      ) : null}
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
