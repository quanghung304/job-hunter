import { CircleAlert, Clock, FileText } from 'lucide-react'
import type { AnalysisStage } from '@/lib/labels'
import { Tag } from './Tag'

/**
 * Analysis state (ADR-0010): "Extracted" when analyzed and not scored; "Analysis failed"; "Not
 * analyzed" only where `showNotAnalyzed` is set (detail header). Scored jobs show only their score.
 */
export function AnalysisTag({ stage, showNotAnalyzed = false }: { stage: AnalysisStage; showNotAnalyzed?: boolean }) {
  if (stage === 'ANALYZED') {
    return (
      <Tag className="bg-extracted-bg text-extracted-fg" icon={FileText}>
        Extracted
      </Tag>
    )
  }
  if (stage === 'FAILED') {
    return (
      <Tag className="bg-strong text-white" icon={CircleAlert}>
        Analysis failed
      </Tag>
    )
  }
  if (stage === 'NOT_ANALYZED' && showNotAnalyzed) {
    return (
      <Tag className="bg-transparent text-muted shadow-[inset_0_0_0_1px_var(--control-border)]" icon={Clock}>
        Not analyzed
      </Tag>
    )
  }
  return null
}
