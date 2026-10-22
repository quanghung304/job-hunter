import { useQueryClient } from '@tanstack/react-query'
import { Info, TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { useBeforeUnload, useBlocker } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '@/api/client'
import { type ProfileView, queryKeys, useProfile, useSaveProfile } from '@/api/queries'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { Tag } from '@/components/Tag'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { errorText } from '@/features/job/useStatusChanger'
import { dateTime } from '@/lib/format'
import { ProfileForm } from './ProfileForm'
import { useProfileForm } from './useProfileForm'

/** `/profile` (ADR-0012): the owner's single profile, edited as a draft and saved to profile.yaml. */
export function ProfilePage() {
  const profile = useProfile()
  return (
    <main className="mx-auto flex max-w-[960px] flex-col gap-4 px-4 pt-6 pb-16 lg:px-6">
      {profile.isPending ? (
        <ListSkeleton rows={4} />
      ) : profile.isError ? (
        <ErrorState error={profile.error} title="Could not load the profile" />
      ) : (
        // A new hash (after a save or a reload) starts a fresh draft.
        <ProfileEditor key={profile.data.hash} loaded={profile.data} />
      )}
    </main>
  )
}

function ProfileEditor({ loaded }: { loaded: ProfileView }) {
  const form = useProfileForm(loaded.profile)
  const save = useSaveProfile()
  const client = useQueryClient()
  const [conflict, setConflict] = useState<ApiError | null>(null)
  const guard = form.dirty && !save.isSuccess

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) => guard && currentLocation.pathname !== nextLocation.pathname,
  )
  useBeforeUnload((event) => {
    if (guard) event.preventDefault()
  })

  const submit = () =>
    save.mutate(
      { baseHash: loaded.hash, profile: form.draft },
      {
        onSuccess: (saved) => {
          const n = saved.reassessed.jobs
          toast(`Profile saved · eligibility re-checked for ${n} ${n === 1 ? 'job' : 'jobs'}`)
        },
        onError: (error) => {
          if (error instanceof ApiError && error.code === 'PROFILE_CHANGED') {
            setConflict(error)
          } else if (error instanceof ApiError && error.errors.length > 0) {
            form.setServerProblems(error.errors)
          } else {
            toast.error(`Profile not saved: ${errorText(error)}`)
          }
        },
      },
    )

  const reload = () => {
    setConflict(null)
    void client.invalidateQueries({ queryKey: queryKeys.profile })
  }

  const count = form.problems.length
  return (
    <>
      <header className="flex flex-wrap items-center gap-x-4 gap-y-3 rounded-panel border border-card-border bg-surface px-6 py-5 max-lg:px-4">
        <div className="min-w-0 flex-1">
          <h1 className="m-0 flex flex-wrap items-baseline gap-x-2 text-[22px] font-semibold tracking-[-0.01em] text-ink">
            Your profile <span className="num text-[15px] font-medium text-muted">{loaded.id}</span>
          </h1>
          <p className="num mt-1 mb-0 text-[12.5px] text-muted">
            Saved {dateTime(loaded.savedAt)} · hash {loaded.hash.slice(0, 6)} · config/profile.yaml
          </p>
        </div>
        {form.dirty ? (
          <Tag className="bg-attention-bg font-semibold text-attention-fg" icon={TriangleAlert}>
            Unsaved changes
          </Tag>
        ) : (
          <Tag className="bg-neutral-fill text-ink-2">No changes</Tag>
        )}
        <div className="flex items-center gap-2">
          <Button variant="quiet" disabled={!form.dirty || save.isPending} onClick={form.discard}>
            Discard
          </Button>
          <Button variant="primary" disabled={!form.dirty || !form.valid || save.isPending} onClick={submit}>
            {save.isPending ? 'Saving…' : 'Save changes'}
          </Button>
        </div>
      </header>

      {conflict ? (
        <ErrorState
          error={conflict}
          title="The profile changed since you loaded it"
          action={
            <Button variant="secondary" onClick={reload}>
              Reload
            </Button>
          }
        />
      ) : null}

      {count > 0 ? (
        <section role="alert" className="rounded-card bg-attention-bg px-5 py-3.5 text-attention-fg">
          <p className="m-0 flex items-center gap-2 font-semibold">
            <TriangleAlert className="size-4 text-attention-icon" aria-hidden />
            Fix {count} {count === 1 ? 'field' : 'fields'} to save
          </p>
          <ul className="num mt-1.5 mb-0 pl-6 text-[12.5px]">
            {form.problems.map((problem) => (
              <li key={`${problem.path}-${problem.message}`}>
                {problem.path}: {problem.message}
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <p className="m-0 flex gap-2 rounded-card border border-line bg-surface-muted px-4 py-3 text-[13.5px] text-ink-2">
        <Info className="mt-0.5 size-4 shrink-0 text-muted" aria-hidden />
        Saving re-runs the prefilter and the eligibility criteria for this profile (no LLM calls). Scores are kept;
        re-score from Admin when you want them updated.
      </p>

      <ProfileForm form={form} />

      <Dialog open={blocker.state === 'blocked'} onOpenChange={(open) => !open && blocker.reset?.()}>
        <DialogContent className="rounded-dialog shadow-dialog sm:max-w-[420px]" showCloseButton={false}>
          <DialogHeader>
            <DialogTitle className="text-[17px] font-semibold">Leave without saving?</DialogTitle>
            <DialogDescription className="text-[13.5px] text-ink-2">
              Your changes to the profile are not saved and will be lost.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="secondary" onClick={() => blocker.reset?.()}>
              Stay
            </Button>
            <Button variant="primary" onClick={() => blocker.proceed?.()}>
              Leave
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}
