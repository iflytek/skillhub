import { describe, expect, it } from 'vitest'
import {
  SELECT_CONTENT_CLASS_NAME,
  SELECT_ITEM_CLASS_NAME,
  SELECT_SCROLL_BUTTON_CLASS_NAME,
  SELECT_TRIGGER_CLASS_NAME,
  normalizeSelectValue,
} from './select'

describe('shared select contract', () => {
  it('keeps the trigger aligned with the existing input styling language', () => {
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('h-11')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('rounded-lg')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('border-border/60')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('bg-secondary/50')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('focus-visible:outline-none')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('focus-visible:ring-2')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('focus-visible:ring-primary/40')
    expect(SELECT_TRIGGER_CLASS_NAME).toContain('focus-visible:border-primary/50')
  })

  it('uses themed panel and item classes for the floating listbox', () => {
    expect(SELECT_CONTENT_CLASS_NAME).toContain('bg-popover')
    expect(SELECT_CONTENT_CLASS_NAME).toContain('text-popover-foreground')
    expect(SELECT_ITEM_CLASS_NAME).toContain('focus:bg-accent')
    expect(SELECT_ITEM_CLASS_NAME).toContain('data-[disabled]:opacity-50')
  })

  it('keeps the dropdown and selected items visually discoverable', () => {
    expect(SELECT_CONTENT_CLASS_NAME).toContain('shadow-md')
    expect(SELECT_ITEM_CLASS_NAME).toContain('pl-8')
    expect(SELECT_ITEM_CLASS_NAME).toContain('rounded-md')
  })

  it('uses pointer cursors for expanded select interactions', () => {
    expect(SELECT_ITEM_CLASS_NAME).toContain('cursor-pointer')
    expect(SELECT_SCROLL_BUTTON_CLASS_NAME).toContain('cursor-pointer')
  })

  it('maps empty and nullish form state to an undefined Radix value', () => {
    expect(normalizeSelectValue('')).toBeUndefined()
    expect(normalizeSelectValue(null)).toBeUndefined()
    expect(normalizeSelectValue(undefined)).toBeUndefined()
    expect(normalizeSelectValue('PUBLIC')).toBe('PUBLIC')
  })
})
