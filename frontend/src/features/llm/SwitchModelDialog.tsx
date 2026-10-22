import { TriangleAlert } from 'lucide-react'
import { useState } from 'react'
import { toast } from 'sonner'
import { cn } from 'cn'
import { type LlmModel, type LlmPurpose, useLlmAdmin, useSwitchModel } from '@/api/queries'
import { ErrorState } from '@/components/ErrorState'
import { ListSkeleton } from '@/components/ListSkeleton'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { evalText, purposeAction, purposeLabel, unusableReason, usageText } from './llmLabels'
import { ModelStatusTag } from './ModelStatusTag'
import { TierTag } from './TierTag'

/**
 * Pick the model of Extraction or Scoring (frontend.md `SwitchModelDialog`, ADR-0008): one row per
 * model with provider, tier, status, usage today and last eval; ERROR/EXHAUSTED models (and PAID
 * ones while paid calls are off) are disabled with the reason. A PAID choice needs an explicit
 * confirmation (ADR-0009). Saving makes no LLM call; it un-pauses the step.
 */
export function SwitchModelDialog({
  purpose,
  open,
  onOpenChange,
  preselect,
}: {
  purpose: LlmPurpose
  open: boolean
  onOpenChange: (open: boolean) => void
  /** A model to pre-select, e.g. the one to switch back to. */
  preselect?: string
}) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="rounded-dialog shadow-dialog sm:max-w-[560px]">
        {open ? <SwitchModelForm purpose={purpose} preselect={preselect} onDone={() => onOpenChange(false)} /> : null}
      </DialogContent>
    </Dialog>
  )
}

function SwitchModelForm({
  purpose,
  preselect,
  onDone,
}: {
  purpose: LlmPurpose
  preselect?: string
  onDone: () => void
}) {
  const llm = useLlmAdmin()
  const switchModel = useSwitchModel()
  const [chosen, setChosen] = useState<string | null>(preselect ?? null)
  const [paidConfirmed, setPaidConfirmed] = useState(false)
  const step = purposeLabel(purpose)

  const header = (
    <DialogHeader>
      <DialogTitle className="text-[17px] font-semibold">Model for {step}</DialogTitle>
      <DialogDescription className="text-[13.5px] text-ink-2">
        Used by every {purposeAction(purpose)}. Switching makes no LLM call; existing results are kept.
      </DialogDescription>
    </DialogHeader>
  )
  if (llm.isPending) {
    return (
      <>
        {header}
        <ListSkeleton rows={3} />
      </>
    )
  }
  if (llm.isError) {
    return (
      <>
        {header}
        <ErrorState error={llm.error} title="Could not load the models" />
      </>
    )
  }

  const assignment = llm.data.assignments.find((a) => a.purpose === purpose)
  const current = assignment?.modelId ?? null
  const currentIsFake = llm.data.models.find((m) => m.modelId === current)?.provider === 'fake'
  // The test-only fake model is offered only while a fake model serves the step.
  const models = llm.data.models.filter((m) => m.provider !== 'fake' || currentIsFake)
  const selectedId = chosen ?? current
  const selected = models.find((m) => m.modelId === selectedId)
  const paid = selected?.tier === 'PAID'
  const canSave =
    !!selected &&
    selected.modelId !== current &&
    unusableReason(selected, llm.data.paidCallsAllowed) === null &&
    (!paid || paidConfirmed) &&
    !switchModel.isPending

  const save = () => {
    if (!selected) return
    switchModel.mutate(
      { purpose, modelId: selected.modelId, confirmPaid: paid ? true : undefined },
      {
        onSuccess: () => {
          toast(`${step} now uses ${selected.label}`)
          onDone()
        },
      },
    )
  }

  return (
    <>
      {header}
      <fieldset className="m-0 flex max-h-[50vh] flex-col gap-1.5 overflow-y-auto border-0 p-0">
        <legend className="sr-only">Models</legend>
        {models.map((model) => (
          <ModelOption
            key={model.modelId}
            model={model}
            current={model.modelId === current}
            checked={model.modelId === selectedId}
            reason={model.modelId === current ? null : unusableReason(model, llm.data.paidCallsAllowed)}
            onSelect={() => {
              setChosen(model.modelId)
              setPaidConfirmed(false)
            }}
          />
        ))}
      </fieldset>
      {paid && selected && selected.modelId !== current ? (
        <div className="flex flex-col gap-2 rounded-card bg-attention-bg px-3 py-2.5 text-[13.5px] text-attention-fg">
          <p className="m-0 flex gap-2">
            <TriangleAlert className="mt-0.5 size-4 shrink-0 text-attention-icon" aria-hidden />
            Paid model — every {purposeAction(purpose)} costs money and counts against the monthly budget.
          </p>
          <label className="flex items-center gap-2 font-medium">
            <Checkbox checked={paidConfirmed} onCheckedChange={(value) => setPaidConfirmed(value === true)} />I confirm
            paid calls for {step}
          </label>
        </div>
      ) : null}
      {switchModel.isError ? <ErrorState error={switchModel.error} title="Not switched" /> : null}
      <DialogFooter>
        <Button variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button variant="primary" disabled={!canSave} onClick={save}>
          {switchModel.isPending ? 'Saving…' : 'Save'}
        </Button>
      </DialogFooter>
    </>
  )
}

function ModelOption({
  model,
  current,
  checked,
  reason,
  onSelect,
}: {
  model: LlmModel
  current: boolean
  checked: boolean
  reason: string | null
  onSelect: () => void
}) {
  const disabled = reason !== null
  return (
    <label
      className={cn(
        'flex cursor-pointer gap-3 rounded-card border px-3 py-2.5',
        checked ? 'border-accent bg-accent-tint-2' : 'border-line bg-surface hover:bg-surface-muted',
        disabled && 'cursor-not-allowed opacity-75 hover:bg-surface',
      )}
    >
      <input
        type="radio"
        name="model"
        className="mt-1 accent-accent"
        checked={checked}
        disabled={disabled}
        onChange={onSelect}
        aria-describedby={`model-${model.modelId}-meta`}
      />
      <span className="flex min-w-0 flex-1 flex-col gap-1">
        <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="font-semibold text-ink">{model.label}</span>
          <span className="text-[12.5px] text-muted">{model.provider}</span>
          <TierTag tier={model.tier} />
          <ModelStatusTag status={model.status} until={model.until} />
          {current ? <span className="text-[12.5px] font-medium text-accent-deep">current</span> : null}
        </span>
        <span id={`model-${model.modelId}-meta`} className="flex flex-col gap-0.5">
          <span className="num text-[12.5px] text-ink-2">{usageText(model)}</span>
          <span className="text-[12.5px] text-muted">{evalText(model)}</span>
          {reason ? <span className="text-[12.5px] text-muted">{reason}</span> : null}
        </span>
      </span>
    </label>
  )
}
