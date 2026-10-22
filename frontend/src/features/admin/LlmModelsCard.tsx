import { Loader2 } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { cn } from 'cn'
import {
  type LlmAdmin,
  type LlmAssignment,
  type LlmModel,
  useLlmAdmin,
  useSwitchModel,
  useTestModel,
} from '@/api/queries'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { SectionCard } from '@/components/SectionCard'
import { Tag } from '@/components/Tag'
import { Button } from '@/components/ui/button'
import { errorText } from '@/features/job/useStatusChanger'
import { evalText, purposeLabel } from '@/features/llm/llmLabels'
import { ModelStatusTag } from '@/features/llm/ModelStatusTag'
import { PaidConfirmDialog } from '@/features/llm/PaidConfirmDialog'
import { type PaidConfirmation, paidConfirmationOf } from '@/features/llm/paidConfirmation'
import { SwitchModelDialog } from '@/features/llm/SwitchModelDialog'
import { TierTag } from '@/features/llm/TierTag'
import { clockTime, compactCount, dateTime, usd } from '@/lib/format'

const REASONS: Record<string, string> = { USER: 'User', AUTO_FALLBACK: 'Auto-fallback', RESTORED: 'Restored' }

const TH = 'border-b border-line px-3 py-2 text-left text-[12px] font-semibold tracking-[0.04em] text-muted uppercase'
const TD = 'border-b border-line-faint px-3 py-2.5 align-middle'

/** An on/off setting as a switch (`role="switch"`, word next to it). */
function Toggle({
  label,
  checked,
  disabled,
  hint,
  onChange,
}: {
  label: string
  checked: boolean
  disabled?: boolean
  hint?: string
  onChange: (value: boolean) => void
}) {
  return (
    <span className="flex flex-col gap-0.5">
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={() => onChange(!checked)}
        className="flex items-center gap-2 text-left text-[13.5px] text-ink-2 disabled:text-muted"
      >
        <span
          className={cn(
            'relative inline-flex h-5 w-9 shrink-0 rounded-full border transition-colors',
            checked ? 'border-accent bg-accent' : 'border-control-border bg-neutral-fill',
          )}
          aria-hidden
        >
          <span
            className={cn(
              'absolute top-0.5 size-3.5 rounded-full bg-surface shadow-[0_0_0_1px_var(--control-border)] transition-[left]',
              checked ? 'left-[18px]' : 'left-0.5',
            )}
          />
        </span>
        {label}
        <span className="num text-[12px] text-muted">{checked ? 'on' : 'off'}</span>
      </button>
      {hint ? <span className="pl-11 text-[12px] text-muted">{hint}</span> : null}
    </span>
  )
}

function AssignmentBlock({
  assignment,
  data,
  onChange,
  onToggle,
}: {
  assignment: LlmAssignment
  data: LlmAdmin
  onChange: () => void
  onToggle: (patch: { autoFallback?: boolean; allowPaid?: boolean }) => void
}) {
  const model = data.models.find((m) => m.modelId === assignment.modelId)
  const label = (id: string) => data.models.find((m) => m.modelId === id)?.label ?? id
  const limit = model?.limits.rpd ?? null
  const used = model?.usageToday.calls ?? 0
  const ratio = limit ? Math.min(1, used / limit) : 0
  return (
    <section
      aria-label={purposeLabel(assignment.purpose)}
      className="flex flex-col gap-3 rounded-card border border-line bg-surface-muted px-4 py-3.5"
    >
      <h3 className="m-0 text-[13px] font-semibold tracking-[0.05em] text-muted uppercase">
        {purposeLabel(assignment.purpose)}
      </h3>
      <p className="m-0 flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{model?.label ?? assignment.modelId}</span>
        {model ? <TierTag tier={model.tier} /> : null}
        {model ? <ModelStatusTag status={model.status} until={model.until} /> : null}
      </p>
      <div>
        {limit ? (
          <div
            role="meter"
            aria-label={`${purposeLabel(assignment.purpose)} requests today`}
            aria-valuemin={0}
            aria-valuemax={limit}
            aria-valuenow={used}
            className="h-2 overflow-hidden rounded-badge bg-neutral-fill"
          >
            <span
              className={cn('block h-full', ratio >= 0.8 ? 'bg-attention-fg' : 'bg-accent')}
              style={{ width: `${ratio * 100}%` }}
            />
          </div>
        ) : null}
        <p className="num mt-1 mb-0 text-[12.5px] text-muted">
          {limit ? `${used} / ${limit} requests today` : `${used} requests today · no known limit`}
          {model?.until ? ` · resets ${clockTime(model.until)}` : ''}
        </p>
      </div>
      <Toggle
        label="Auto-switch to fallback"
        checked={assignment.autoFallback}
        onChange={(autoFallback) => onToggle({ autoFallback })}
      />
      <Toggle
        label="Allow paid fallback"
        checked={assignment.allowPaid}
        disabled={!data.paidCallsAllowed && !assignment.allowPaid}
        hint={data.paidCallsAllowed ? undefined : 'Paid calls are off (LLM_ALLOW_PAID_CALLS=false)'}
        onChange={(allowPaid) => onToggle({ allowPaid })}
      />
      <div className="flex flex-wrap items-center gap-1.5">
        <span className="text-[12.5px] text-muted">Fallbacks</span>
        {assignment.fallbacks.length === 0 ? <span className="text-[12.5px] text-muted">none</span> : null}
        {assignment.fallbacks.map((id, i) => (
          <Tag key={id} className="bg-line-faint text-strong">
            {`${i + 1}. ${label(id)}`}
          </Tag>
        ))}
      </div>
      <div>
        <Button variant="secondary" onClick={onChange}>
          Change model
        </Button>
      </div>
    </section>
  )
}

/**
 * Admin › LLM models (`#llm`, ADR-0008): the model of each step with usage and toggles, every
 * registry model with status, today's usage, limits and a "Test" button, and the switch history.
 * PAID actions need the owner's confirmation (ADR-0009).
 */
export function LlmModelsCard() {
  const llm = useLlmAdmin()
  const switchModel = useSwitchModel()
  const testModel = useTestModel()
  const [switching, setSwitching] = useState<LlmAssignment['purpose'] | null>(null)
  const [confirmation, setConfirmation] = useState<PaidConfirmation | null>(null)

  const toggle = (assignment: LlmAssignment, patch: { autoFallback?: boolean; allowPaid?: boolean }) => {
    const send = (confirmPaid: boolean) =>
      switchModel.mutate(
        { purpose: assignment.purpose, modelId: assignment.modelId, ...patch, confirmPaid: confirmPaid || undefined },
        {
          onSuccess: () => toast(`${purposeLabel(assignment.purpose)} settings saved`),
          onError: (error) => {
            const paid = paidConfirmationOf(error, `Allow paid fallback for ${purposeLabel(assignment.purpose)}`, () =>
              send(true),
            )
            if (paid) setConfirmation(paid)
            else toast.error(`Not saved: ${errorText(error)}`)
          },
        },
      )
    send(false)
  }

  const test = (model: LlmModel) => {
    const send = (confirmPaid: boolean) =>
      testModel.mutate(
        { modelId: model.modelId, confirmPaid },
        {
          onSuccess: (result) =>
            result.ok
              ? toast(`${model.label} works · ${result.latencyMs} ms`)
              : toast.error(`${model.label} test failed: ${result.detail ?? result.status}`),
          onError: (error) => {
            const paid = paidConfirmationOf(error, `Test ${model.label}`, () => send(true))
            if (paid) setConfirmation(paid)
            else toast.error(`Test not run: ${errorText(error)}`)
          },
        },
      )
    send(false)
  }

  let body
  if (llm.isPending) {
    body = <ListSkeleton rows={3} />
  } else if (llm.isError) {
    body = <ErrorState error={llm.error} title="Could not load the LLM models" />
  } else {
    const data = llm.data
    const assigned = new Set(data.assignments.map((a) => a.modelId))
    const models = data.models.filter((m) => m.provider !== 'fake' || assigned.has(m.modelId))
    const label = (id: string) => data.models.find((m) => m.modelId === id)?.label ?? id
    body = (
      <div className="flex flex-col gap-5">
        <div className="grid grid-cols-[repeat(auto-fit,minmax(min(360px,100%),1fr))] gap-4">
          {data.assignments.map((assignment) => (
            <AssignmentBlock
              key={assignment.purpose}
              assignment={assignment}
              data={data}
              onChange={() => setSwitching(assignment.purpose)}
              onToggle={(patch) => toggle(assignment, patch)}
            />
          ))}
        </div>
        <div className="overflow-x-auto">
          <table className="w-full border-collapse text-[13.5px]">
            <thead>
              <tr>
                <th className={TH}>Model</th>
                <th className={TH}>Provider</th>
                <th className={TH}>Tier</th>
                <th className={TH}>Status</th>
                <th className={TH}>Today</th>
                <th className={TH}>Limits</th>
                <th className={TH}>Last eval</th>
                <th className={TH}>
                  <span className="sr-only">Test</span>
                </th>
              </tr>
            </thead>
            <tbody>
              {models.map((model) => {
                const testing = testModel.isPending && testModel.variables?.modelId === model.modelId
                return (
                  <tr key={model.modelId}>
                    <td className={`${TD} font-medium text-ink`}>
                      {model.label}
                      {model.keyConfigured ? null : (
                        <span className="block text-[12px] font-normal text-muted">
                          Add the {model.provider} API key to .env
                        </span>
                      )}
                    </td>
                    <td className={`${TD} text-ink-2`}>{model.provider}</td>
                    <td className={TD}>
                      <TierTag tier={model.tier} />
                    </td>
                    <td className={TD}>
                      <ModelStatusTag status={model.status} until={model.until} />
                      {model.detail && model.status !== 'AVAILABLE' ? (
                        <span className="block max-w-[260px] truncate text-[12px] text-muted" title={model.detail}>
                          {model.detail}
                        </span>
                      ) : null}
                    </td>
                    <td className={`${TD} num text-[12.5px] whitespace-nowrap`}>
                      {model.usageToday.calls} calls ·{' '}
                      {compactCount(model.usageToday.inputTokens + model.usageToday.outputTokens)} tokens
                      {model.usageToday.costUsd !== null ? ` · ${usd(model.usageToday.costUsd)}` : ''}
                    </td>
                    <td className={`${TD} num text-[12.5px] whitespace-nowrap text-muted`}>
                      {[
                        model.limits.rpm ? `${model.limits.rpm}/min` : null,
                        model.limits.rpd ? `${model.limits.rpd}/day` : null,
                        model.limits.tokensPerDay ? `${compactCount(model.limits.tokensPerDay)} tok/day` : null,
                        model.limits.requestsPerMonth ? `${model.limits.requestsPerMonth}/month` : null,
                      ]
                        .filter(Boolean)
                        .join(' · ') || '—'}
                    </td>
                    <td className={`${TD} text-[12.5px] text-muted`}>{evalText(model)}</td>
                    <td className={`${TD} text-right`}>
                      <Button
                        variant="secondary"
                        size="sm"
                        disabled={testing || !model.keyConfigured || (model.tier === 'PAID' && !data.paidCallsAllowed)}
                        title={
                          model.tier === 'PAID' && !data.paidCallsAllowed
                            ? 'Paid calls are off (LLM_ALLOW_PAID_CALLS=false)'
                            : 'One tiny call to this model'
                        }
                        onClick={() => test(model)}
                      >
                        {testing ? <Loader2 className="animate-spin" aria-hidden /> : null}
                        {testing ? 'Testing…' : 'Test'}
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <div>
          <h3 className="mt-0 mb-2 text-[13px] font-semibold tracking-[0.05em] text-muted uppercase">Switch history</h3>
          {data.history.length === 0 ? (
            <p className="m-0 text-[13.5px] text-muted">No switches yet.</p>
          ) : (
            <ul className="m-0 flex list-none flex-col p-0">
              {data.history.map((event) => (
                <li
                  key={`${event.at}-${event.purpose}-${event.toModelId}`}
                  className="flex flex-wrap gap-x-3 border-b border-line-faint py-1.5 text-[13.5px] text-ink-2 last:border-b-0"
                >
                  <time className="num w-28 shrink-0 text-[12.5px] text-muted" dateTime={event.at}>
                    {dateTime(event.at)}
                  </time>
                  <span>
                    {purposeLabel(event.purpose)}: {label(event.fromModelId)} → {label(event.toModelId)}
                  </span>
                  <span className="text-muted">{REASONS[event.reason] ?? event.reason}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    )
  }

  return (
    <SectionCard id="llm" label="LLM models" className="scroll-mt-4">
      {body}
      {switching ? (
        <SwitchModelDialog purpose={switching} open onOpenChange={(open) => !open && setSwitching(null)} />
      ) : null}
      <PaidConfirmDialog confirmation={confirmation} onClose={() => setConfirmation(null)} />
    </SectionCard>
  )
}
