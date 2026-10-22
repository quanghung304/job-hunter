import { Gauge, RotateCcw, TriangleAlert, X } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'
import { cn } from 'cn'
import { type LlmAlert, useStatus } from '@/api/queries'
import { Button } from '@/components/ui/button'
import { clockTime } from '@/lib/format'
import { alertIdentity, purposeAction, purposeLabel } from './llmLabels'
import { SwitchModelDialog } from './SwitchModelDialog'

const DISMISSED_KEY = 'llm.dismissedAlerts'

function readDismissed(): string[] {
  try {
    const raw = window.sessionStorage.getItem(DISMISSED_KEY)
    return raw ? (JSON.parse(raw) as string[]) : []
  } catch {
    return []
  }
}

function writeDismissed(ids: string[]) {
  try {
    window.sessionStorage.setItem(DISMISSED_KEY, JSON.stringify(ids))
  } catch {
    // storage unavailable: dismissed for this page only
  }
}

/** "81%" from "Daily request limit 812/1000 (81%)". */
function percentOf(detail: string | null): string | null {
  return detail?.match(/(\d+)%/)?.[1] ?? null
}

/** Heading and tone of one alert (frontend.md "LLM alert banner"). */
function describe(alert: LlmAlert): { title: string; text: string; attention: boolean } {
  const step = purposeLabel(alert.purpose)
  if (alert.switchedFromModelId) {
    const back = alert.suggestions.find((s) => s.modelId === alert.switchedFromModelId)?.label
    return {
      title: `${step} uses ${alert.modelLabel} after an auto-switch`,
      text: `${back ?? alert.switchedFromModelId} works again`,
      attention: false,
    }
  }
  if (alert.paused) {
    return {
      title: `${step} paused`,
      text: `${alert.modelLabel}: ${alert.detail ?? alert.status.toLowerCase()} · ${purposeAction(alert.purpose)} is off`,
      attention: true,
    }
  }
  if (alert.status === 'NEAR_LIMIT') {
    const percent = percentOf(alert.detail)
    return {
      title: percent ? `${step} model at ${percent}% of today's limit` : `${step} model near its limit`,
      text: alert.detail ? `${alert.modelLabel}: ${alert.detail}` : alert.modelLabel,
      attention: false,
    }
  }
  return {
    title: `${step} model ${alert.status === 'ERROR' ? 'has an error' : 'is limited'}`,
    text: `${alert.modelLabel}: ${alert.detail ?? alert.status.toLowerCase()} · a fallback model is used`,
    attention: alert.status === 'ERROR' || alert.status === 'EXHAUSTED',
  }
}

/**
 * LLM alerts under the app header on every page (ADR-0008): one row per alert with "Switch model"
 * (the dialog for that step), "Details" (Admin › LLM models) and dismiss for this session. The
 * status poll runs every 10 s while an alert exists, so a switch clears the row on the next poll.
 */
export function LlmAlertBanner() {
  const status = useStatus()
  const [dismissed, setDismissed] = useState<string[]>(readDismissed)
  const [switching, setSwitching] = useState<LlmAlert | null>(null)
  const visible = (status.data?.llmAlerts ?? []).filter((alert) => !dismissed.includes(alertIdentity(alert)))
  if (visible.length === 0 && !switching) return null

  const dismiss = (alert: LlmAlert) => {
    const next = [...dismissed, alertIdentity(alert)]
    setDismissed(next)
    writeDismissed(next)
  }

  return (
    <>
      <div role="region" aria-label="LLM alerts">
        {visible.map((alert) => {
          const { title, text, attention } = describe(alert)
          const Icon = alert.switchedFromModelId ? RotateCcw : attention ? TriangleAlert : Gauge
          return (
            <div
              key={alertIdentity(alert)}
              role={attention ? 'alert' : 'status'}
              className={cn(
                'flex flex-wrap items-center gap-x-3 gap-y-2 border-b border-line px-6 py-2.5 max-lg:px-4',
                attention ? 'bg-attention-bg text-attention-fg' : 'bg-surface-muted text-ink-2',
              )}
            >
              <Icon className={cn('size-4 shrink-0', attention ? 'text-attention-icon' : 'text-muted')} aria-hidden />
              <p className="m-0 min-w-0 flex-1 text-[13.5px]">
                <span className="font-semibold">{title}</span> · {text}
                {alert.until && alert.status !== 'AVAILABLE' ? (
                  <span className="num"> · resets {clockTime(alert.until)}</span>
                ) : null}
              </p>
              <span className="flex items-center gap-2">
                <Button variant="secondary" size="sm" onClick={() => setSwitching(alert)}>
                  {alert.switchedFromModelId ? 'Switch back' : 'Switch model'}
                </Button>
                <Link to="/admin#llm" className="text-[13px] underline underline-offset-2">
                  Details
                </Link>
                <Button
                  variant="quiet"
                  size="icon"
                  className="max-lg:size-11"
                  aria-label={`Dismiss: ${title}`}
                  title="Hide for this session"
                  onClick={() => dismiss(alert)}
                >
                  <X className="size-4" />
                </Button>
              </span>
            </div>
          )
        })}
      </div>
      {switching ? (
        <SwitchModelDialog
          purpose={switching.purpose}
          open
          onOpenChange={(open) => !open && setSwitching(null)}
          preselect={switching.switchedFromModelId ?? undefined}
        />
      ) : null}
    </>
  )
}
