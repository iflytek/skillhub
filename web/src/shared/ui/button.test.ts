import { describe, expect, it } from 'vitest'
import { buttonVariants } from './button'

describe('buttonVariants', () => {
  it('applies default variant and size classes', () => {
    const classes = buttonVariants()
    expect(classes).toContain('bg-primary')
    expect(classes).toContain('h-[var(--control-height)]')
    expect(classes).toContain('px-[var(--control-padding-x)]')
  })

  it('applies destructive variant classes', () => {
    const classes = buttonVariants({ variant: 'destructive' })
    expect(classes).toContain('bg-destructive')
  })

  it('applies outline variant classes', () => {
    const classes = buttonVariants({ variant: 'outline' })
    expect(classes).toContain('border')
    expect(classes).toContain('bg-background')
  })

  it('applies secondary variant classes', () => {
    const classes = buttonVariants({ variant: 'secondary' })
    expect(classes).toContain('bg-secondary')
  })

  it('keeps the soft variant on the neutral theme', () => {
    const classes = buttonVariants({ variant: 'soft' })
    expect(classes).toContain('bg-primary/5')
    expect(classes).toContain('text-primary')
  })

  it('applies ghost variant classes', () => {
    const classes = buttonVariants({ variant: 'ghost' })
    expect(classes).toContain('hover:bg-accent/70')
  })

  it('applies link variant classes', () => {
    const classes = buttonVariants({ variant: 'link' })
    expect(classes).toContain('underline-offset-4')
  })

  it('applies sm size classes', () => {
    const classes = buttonVariants({ size: 'sm' })
    expect(classes).toContain('h-[var(--control-sm-height)]')
    expect(classes).toContain('px-2.5')
  })

  it('applies lg size classes', () => {
    const classes = buttonVariants({ size: 'lg' })
    expect(classes).toContain('h-[var(--control-lg-height)]')
    expect(classes).toContain('px-3')
  })

  it('applies icon size classes', () => {
    const classes = buttonVariants({ size: 'icon' })
    expect(classes).toContain('h-[var(--control-icon-size)]')
    expect(classes).toContain('w-[var(--control-icon-size)]')
  })

  it('always includes base focus-visible and disabled styles', () => {
    const classes = buttonVariants()
    expect(classes).toContain('focus-visible:outline-2')
    expect(classes).toContain('disabled:pointer-events-none')
    expect(classes).toContain('disabled:opacity-50')
  })
})
