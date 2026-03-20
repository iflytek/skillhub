import { describe, expect, it } from 'vitest'
import {
  SELECT_CONTENT_CLASS_NAME,
  SELECT_ITEM_CLASS_NAME,
  SELECT_TRIGGER_CLASS_NAME,
  normalizeSelectValue,
} from './select'

describe('shared select contract', () => {
  it('keeps the trigger aligned with the existing input styling language', () => {
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('h-11')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('rounded-lg')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('border-border')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('focus:ring-primary/40')
  })

  it('uses themed panel and item classes for the floating listbox', () => {
    expect(SELECT_CONTENT_CLASS_NAME).toContain('bg-popover')
    expect(SELECT_CONTENT_CLASS_NAME).toContain('text-popover-foreground')
    expect(SELECT_ITEM_CLASS_NAME).toContain('focus:bg-accent')
    expect(SELECT_ITEM_CLASS_NAME).toContain('data-[disabled]:opacity-50')
  })

  it('maps empty string form state to an undefined Radix value', () => {
    expect(normalizeSelectValue('')).toBeUndefined()
    expect(normalizeSelectValue('PUBLIC')).toBe('PUBLIC')
  })
})
