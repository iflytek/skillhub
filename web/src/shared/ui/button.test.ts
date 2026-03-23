import { describe, expect, it } from 'vitest'
import { buttonVariants } from './button'

describe('buttonVariants', () => {
  it('applies default variant and size classes', () => {
    const classes = buttonVariants()
    expect(classes).toContain('bg-brand-gradient')
    expect(classes).toContain('h-10')
    expect(classes).toContain('px-5')
  })

  it('applies destructive variant classes', () => {
    const classes = buttonVariants({ variant: 'destructive' })
    expect(classes).toContain('bg-destructive')
  })

  it('applies outline variant classes', () => {
    const classes = buttonVariants({ variant: 'outline' })
    expect(classes).toContain('border')
    expect(classes).toContain('bg-transparent')
  })

  it('applies secondary variant classes', () => {
    const classes = buttonVariants({ variant: 'secondary' })
    expect(classes).toContain('bg-secondary')
  })

  it('applies ghost variant classes', () => {
    const classes = buttonVariants({ variant: 'ghost' })
    expect(classes).toContain('hover:bg-secondary')
  })

  it('applies link variant classes', () => {
    const classes = buttonVariants({ variant: 'link' })
    expect(classes).toContain('underline-offset-4')
  })

  it('applies sm size classes', () => {
    const classes = buttonVariants({ size: 'sm' })
    expect(classes).toContain('h-8')
    expect(classes).toContain('text-xs')
  })

  it('applies lg size classes', () => {
    const classes = buttonVariants({ size: 'lg' })
    expect(classes).toContain('h-12')
    expect(classes).toContain('text-base')
  })

  it('applies icon size classes', () => {
    const classes = buttonVariants({ size: 'icon' })
    expect(classes).toContain('h-10')
    expect(classes).toContain('w-10')
  })

  it('always includes base focus-visible and disabled styles', () => {
    const classes = buttonVariants()
    expect(classes).toContain('focus-visible:outline-none')
    expect(classes).toContain('disabled:pointer-events-none')
    expect(classes).toContain('disabled:opacity-50')
  })
})
