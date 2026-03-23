import { describe, expect, it } from 'vitest'
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogFooter,
  DialogTitle,
  DialogDescription,
} from './dialog'

describe('Dialog components', () => {
  it('exports all dialog sub-components', () => {
    expect(Dialog).toBeDefined()
    expect(DialogTrigger).toBeDefined()
    expect(DialogContent).toBeDefined()
    expect(DialogHeader).toBeDefined()
    expect(DialogFooter).toBeDefined()
    expect(DialogTitle).toBeDefined()
    expect(DialogDescription).toBeDefined()
  })

  it('sets displayName on forwardRef components', () => {
    expect(DialogTrigger.displayName).toBe('DialogTrigger')
    expect(DialogContent.displayName).toBe('DialogContent')
    expect(DialogTitle.displayName).toBe('DialogTitle')
    expect(DialogDescription.displayName).toBe('DialogDescription')
  })

  it('sets displayName on function components', () => {
    expect(DialogHeader.displayName).toBe('DialogHeader')
    expect(DialogFooter.displayName).toBe('DialogFooter')
  })
})
