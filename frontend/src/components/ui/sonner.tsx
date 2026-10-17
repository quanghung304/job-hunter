import type * as React from 'react'
import { Toaster as Sonner, type ToasterProps } from 'sonner'
import { CircleCheckIcon, InfoIcon, Loader2Icon, OctagonXIcon, TriangleAlertIcon } from 'lucide-react'

/**
 * Toasts per frontend.md "Toast": bottom-left 24px, ink background, white text, max-width
 * 520px, one at a time, 5 s. Status-change toasts add an "Undo" action (outlined, muted).
 */
const Toaster = (props: ToasterProps) => (
  <Sonner
    theme="light"
    position="bottom-left"
    offset={24}
    mobileOffset={12}
    visibleToasts={1}
    duration={5000}
    className="toaster group"
    icons={{
      success: <CircleCheckIcon className="size-4" />,
      info: <InfoIcon className="size-4" />,
      warning: <TriangleAlertIcon className="size-4" />,
      error: <OctagonXIcon className="size-4" />,
      loading: <Loader2Icon className="size-4 animate-spin" />,
    }}
    style={
      {
        '--normal-bg': 'var(--ink)',
        '--normal-text': 'var(--on-accent)',
        '--normal-border': 'var(--ink)',
        '--border-radius': '6px',
        '--width': '520px',
      } as React.CSSProperties
    }
    toastOptions={{
      classNames: {
        toast: 'font-sans text-[13.5px] shadow-toast max-w-[520px]',
        actionButton: '!bg-transparent !text-white !border !border-muted !rounded-control !font-medium',
        cancelButton: '!bg-transparent !text-white',
      },
    }}
    {...props}
  />
)

export { Toaster }
