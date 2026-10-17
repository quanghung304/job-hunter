import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from 'cn'
import { Slot } from 'radix-ui'

/**
 * Buttons per frontend.md "Shared components": primary (the one next step per view),
 * secondary, quiet, leave-app ("Apply"); disabled = neutral fill. The global focus ring
 * (2px accent outline) applies, so no outline/ring overrides here.
 */
const buttonVariants = cva(
  "inline-flex shrink-0 items-center justify-center gap-1.5 rounded-control border border-transparent font-medium whitespace-nowrap select-none transition-colors disabled:pointer-events-none disabled:border-transparent disabled:bg-neutral-fill disabled:text-muted [&_svg]:pointer-events-none [&_svg]:shrink-0 [&_svg:not([class*='size-'])]:size-4",
  {
    variants: {
      variant: {
        primary: 'bg-accent text-white hover:bg-accent-deep',
        secondary: 'border-control-border bg-surface text-ink hover:bg-surface-muted',
        quiet: 'bg-transparent text-ink-2 hover:bg-line-faint',
        'leave-app': 'bg-ink text-white hover:bg-ink-2',
        link: 'h-auto px-0 text-accent-deep underline underline-offset-4 hover:text-accent',
      },
      size: {
        default: 'h-9 px-3.5 text-[13.5px]',
        sm: 'h-8 px-2.5 text-[13px]',
        lg: 'h-12 px-4 text-[14.5px]',
        icon: 'size-8',
        'icon-touch': 'size-11',
        'icon-lg': 'size-12',
      },
    },
    defaultVariants: {
      variant: 'secondary',
      size: 'default',
    },
  },
)

type ButtonProps = React.ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
    /** Keyboard shortcut shown after the label in mono at 70% opacity (desktop actions). */
    shortcut?: string
  }

function Button({ className, variant, size, asChild = false, shortcut, children, ...props }: ButtonProps) {
  const Comp = asChild ? Slot.Root : 'button'
  return (
    <Comp
      data-slot="button"
      data-variant={variant ?? 'secondary'}
      className={cn(buttonVariants({ variant, size, className }))}
      {...props}
    >
      {asChild ? (
        children
      ) : (
        <>
          {children}
          {shortcut ? <span className="num text-[12px] opacity-70">{shortcut}</span> : null}
        </>
      )}
    </Comp>
  )
}

export { Button, buttonVariants }
