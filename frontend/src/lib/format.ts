import { SALARY_PERIOD_LABELS } from './labels'

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** "just now", "5m", "3h", "2d", "3w", then "12 Oct" (mono, compact; rows and headers). */
export function relativeTime(iso: string | null | undefined, now: number = Date.now()): string {
  if (!iso) return '—'
  const time = Date.parse(iso)
  if (Number.isNaN(time)) return '—'
  const diff = Math.max(0, now - time)
  if (diff < MINUTE) return 'just now'
  if (diff < HOUR) return `${Math.floor(diff / MINUTE)}m`
  if (diff < DAY) return `${Math.floor(diff / HOUR)}h`
  if (diff < 28 * DAY) {
    const days = Math.floor(diff / DAY)
    return days < 14 ? `${days}d` : `${Math.floor(days / 7)}w`
  }
  return shortDate(iso)
}

/** "posted 3d ago" style text for the detail header. */
export function postedAgo(iso: string | null | undefined, now: number = Date.now()): string {
  const rel = relativeTime(iso, now)
  if (rel === '—') return 'posting date unknown'
  if (rel === 'just now') return 'posted just now'
  return /^\d/.test(rel) ? `posted ${rel} ago` : `posted ${rel}`
}

/** "12 Oct" (current year) or "12 Oct 2025". */
export function shortDate(iso: string, now: Date = new Date()): string {
  const date = new Date(iso)
  return date.toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    ...(date.getFullYear() === now.getFullYear() ? {} : { year: 'numeric' }),
  })
}

/** "12 Oct, 07:00" in local time (history entries). */
export function dateTime(iso: string): string {
  return new Date(iso).toLocaleString('en-GB', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** "07:00" in local time. */
export function clockTime(iso: string): string {
  return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
}

const CURRENCY_SYMBOLS: Record<string, string> = { EUR: '€', USD: '$', GBP: '£' }

function amount(value: number): string {
  if (value >= 10_000) {
    const k = value / 1000
    return `${Number.isInteger(k) ? k : k.toFixed(1)}k`
  }
  return String(value)
}

/** "€60k–80k / year", "$45 / hour"; null when no amount is known. */
export function salary(s: {
  min?: number | null
  max?: number | null
  currency?: string | null
  period?: string | null
}): string | null {
  const { min, max } = s
  if ((min === null || min === undefined) && (max === null || max === undefined)) return null
  const currency = s.currency ? (CURRENCY_SYMBOLS[s.currency] ?? `${s.currency} `) : ''
  const range =
    min !== null && min !== undefined && max !== null && max !== undefined && min !== max
      ? `${currency}${amount(min)}–${amount(max)}`
      : `${currency}${amount((min ?? max) as number)}`
  const period = s.period ? ` / ${SALARY_PERIOD_LABELS[s.period] ?? s.period.toLowerCase()}` : ''
  return range + period
}

/** "UTC+1", "UTC−5", "UTC". */
export function utcOffset(hours: number): string {
  if (hours === 0) return 'UTC'
  return hours > 0 ? `UTC+${hours}` : `UTC−${Math.abs(hours)}`
}

/** "UTC−1 to UTC+3, 4h overlap". */
export function timezoneRange(tz: {
  minUtcOffset: number
  maxUtcOffset: number
  overlapHours?: number | null
}): string {
  const range =
    tz.minUtcOffset === tz.maxUtcOffset
      ? utcOffset(tz.minUtcOffset)
      : `${utcOffset(tz.minUtcOffset)} to ${utcOffset(tz.maxUtcOffset)}`
  return tz.overlapHours ? `${range}, ${tz.overlapHours}h overlap` : range
}

/** "30–40 h", "≥ 30 h", "≤ 20 h". */
export function hoursRange(h: { min?: number | null; max?: number | null }): string | null {
  const hasMin = h.min !== null && h.min !== undefined
  const hasMax = h.max !== null && h.max !== undefined
  if (hasMin && hasMax) return h.min === h.max ? `${h.min} h` : `${h.min}–${h.max} h`
  if (hasMin) return `≥ ${h.min} h`
  if (hasMax) return `≤ ${h.max} h`
  return null
}

/** "$1.23"; costs below a cent show 4 decimals ("$0.0040"). */
export function usd(value: number): string {
  return `$${value !== 0 && Math.abs(value) < 0.01 ? value.toFixed(4) : value.toFixed(2)}`
}

/** "950", "12.3k", "1.2M" (token counts). */
export function compactCount(value: number): string {
  if (value < 1000) return String(value)
  if (value < 1_000_000) return `${(value / 1000).toFixed(value < 10_000 ? 1 : 0)}k`
  return `${(value / 1_000_000).toFixed(1)}M`
}

/** "07:00" in the given IANA zone (the pipeline schedule). */
export function clockTimeIn(iso: string, zone: string): string {
  return new Date(iso).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit', timeZone: zone })
}

/** "1m 05s", "12s" (pipeline run durations). */
export function duration(ms: number): string {
  const seconds = Math.round(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  return `${Math.floor(seconds / 60)}m ${String(seconds % 60).padStart(2, '0')}s`
}
