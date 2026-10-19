import { useState } from 'react'
import { NavLink, useLocation } from 'react-router'
import { cn } from 'cn'
import { Briefcase, Menu } from 'lucide-react'
import { useMeta, useStatus } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { clockTime } from '@/lib/format'
import { useIsDesktop } from '@/lib/useMediaQuery'

const NAV = [
  { to: '/', label: 'Board', end: false, match: (path: string) => path === '/' || path.startsWith('/jobs') },
  { to: '/admin', label: 'Admin', end: true, match: (path: string) => path.startsWith('/admin') },
]

/** Height 56px, surface, bottom line (frontend.md "App header"). */
export function AppHeader() {
  return useIsDesktop() ? <DesktopHeader /> : <MobileHeader />
}

function Logo() {
  return (
    <span className="flex items-center gap-2 text-[15px] font-bold text-ink">
      <Briefcase className="size-5 text-accent" strokeWidth={2} aria-hidden />
      Job Hunter
    </span>
  )
}

function DesktopHeader() {
  const { pathname } = useLocation()
  return (
    <header className="flex min-h-14 flex-wrap items-center gap-x-8 gap-y-2 border-b border-line bg-surface px-6">
      <Logo />
      <nav aria-label="Main" className="flex h-14 items-stretch gap-6">
        {NAV.map((item) => {
          const active = item.match(pathname)
          return (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              aria-current={active ? 'page' : undefined}
              className={cn(
                'flex items-center text-[14px] no-underline',
                active ? 'font-semibold text-ink shadow-[inset_0_-2px_0_var(--accent)]' : 'text-muted hover:text-ink',
              )}
            >
              {item.label}
            </NavLink>
          )
        })}
      </nav>
      <div className="ml-auto flex items-center gap-4">
        <StatusLine />
        <ProfilePill />
      </div>
    </header>
  )
}

function MobileHeader() {
  const { pathname } = useLocation()
  const [open, setOpen] = useState(false)
  const current = NAV.find((item) => item.match(pathname))?.label ?? ''
  return (
    <header className="flex h-14 items-center gap-3 border-b border-line bg-surface px-4">
      <Briefcase className="size-5 text-accent" strokeWidth={2} aria-label="Job Hunter" />
      <span className="text-[15px] font-semibold text-ink">{current}</span>
      <div className="ml-auto flex items-center gap-1">
        <ProfilePill />
        <Sheet open={open} onOpenChange={setOpen}>
          <SheetTrigger asChild>
            <Button variant="quiet" size="icon-touch" aria-label="Menu">
              <Menu className="size-5" />
            </Button>
          </SheetTrigger>
          <SheetContent side="right" className="w-64 bg-surface">
            <SheetHeader>
              <SheetTitle>Job Hunter</SheetTitle>
            </SheetHeader>
            <nav aria-label="Main" className="flex flex-col px-4">
              {NAV.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  onClick={() => setOpen(false)}
                  className={cn(
                    'flex min-h-11 items-center text-[15px] no-underline',
                    item.match(pathname) ? 'font-semibold text-ink' : 'text-muted',
                  )}
                >
                  {item.label}
                </NavLink>
              ))}
            </nav>
          </SheetContent>
        </Sheet>
      </div>
    </header>
  )
}

/** "Last run 07:00 · 42 new · 9 scored" from GET /api/status. */
function StatusLine() {
  const status = useStatus()
  const run = status.data?.lastRun
  let text = '—'
  if (status.data?.pipelineRunning) {
    text = 'Pipeline running…'
  } else if (run) {
    text = `Last run ${clockTime(run.startedAt)} · ${run.newJobs} new · ${run.scored} scored`
  } else if (status.data) {
    text = 'No run yet'
  }
  return (
    <span className="num text-[12.5px] text-muted" title={run ? `Last run status: ${run.status}` : 'Pipeline status'}>
      {text}
    </span>
  )
}

function ProfilePill() {
  const meta = useMeta()
  const id = meta.data?.profileId
  return (
    <span
      className="inline-flex h-7 items-center gap-2 rounded-full border border-line bg-surface px-3 text-[13px] text-ink-2"
      title={meta.isError ? 'Profile unavailable: backend not reachable' : 'Active profile'}
    >
      <span className={cn('size-2 rounded-full', meta.isError ? 'bg-neutral-fill' : 'bg-accent')} aria-hidden />
      <span data-testid="profile-id">{id ?? (meta.isError ? 'offline' : '…')}</span>
    </span>
  )
}
