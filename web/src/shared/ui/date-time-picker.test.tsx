// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { Dialog, DialogContent, DialogTitle, DialogDescription } from './dialog'
import { DateTimePicker } from './date-time-picker'

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key, i18n: { language: 'zh' } }),
}))
afterEach(cleanup)
const min = new Date(2030, 0, 10, 12)
const setup = (value = '2030-01-15T14:30') => {
  const onChange = vi.fn()
  render(<DateTimePicker value={value} onChange={onChange} min={min} aria-label="expiry" />)
  fireEvent.click(screen.getByRole('button', { name: 'expiry' }))
  return onChange
}

describe('date time picker', () => {
  it('keeps draft time changes private until applied and preserves local time', async () => {
    const onChange = setup()
    fireEvent.change(screen.getByLabelText('dateTimePicker.hour'), { target: { value: '23' } })
    fireEvent.change(screen.getByLabelText('dateTimePicker.minute'), { target: { value: '45' } })
    expect(onChange).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'dateTimePicker.apply' }))
    expect(onChange).toHaveBeenCalledWith('2030-01-15T23:45')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
  })

  it('discards canceled drafts and restores the saved value when reopened', () => {
    const onChange = setup()
    fireEvent.change(screen.getByLabelText('dateTimePicker.hour'), { target: { value: '23' } })
    fireEvent.click(screen.getByRole('button', { name: 'dialog.cancel' }))
    expect(onChange).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'expiry' }))
    expect((screen.getByLabelText('dateTimePicker.hour') as HTMLInputElement).value).toBe('14')
  })

  it('blocks invalid hours and a time earlier than the minimum', () => {
    const onChange = setup('2030-01-10T14:30')
    const apply = screen.getByRole('button', { name: 'dateTimePicker.apply' }) as HTMLButtonElement
    fireEvent.change(screen.getByLabelText('dateTimePicker.hour'), { target: { value: '25' } })
    expect(apply.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText('dateTimePicker.hour'), { target: { value: '11' } })
    expect(apply.disabled).toBe(true)
    expect(screen.getByRole('alert').textContent).toBe('dateTimePicker.futureRequired')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('handles Enter on time inputs without submitting the containing form', () => {
    const onChange = vi.fn()
    const onSubmit = vi.fn((event) => event.preventDefault())
    render(<form onSubmit={onSubmit}><DateTimePicker value="2030-01-15T14:30" onChange={onChange} min={min} aria-label="expiry" /></form>)
    fireEvent.click(screen.getByRole('button', { name: 'expiry' }))
    fireEvent.keyDown(screen.getByLabelText('dateTimePicker.hour'), { key: 'Enter' })
    expect(onChange).toHaveBeenCalledWith('2030-01-15T14:30')
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('closes only the picker on Escape and returns focus within a parent dialog', async () => {
    render(<Dialog defaultOpen><DialogContent><DialogTitle>Token</DialogTitle><DialogDescription>Expiration</DialogDescription><DateTimePicker value="2030-01-15T14:30" onChange={vi.fn()} min={min} aria-label="expiry" /></DialogContent></Dialog>)
    fireEvent.click(screen.getByRole('button', { name: 'expiry' }))
    expect(screen.getAllByRole('dialog').length).toBe(1)
    fireEvent.keyDown(document.activeElement!, { key: 'Escape' })
    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'Token' })).not.toBeNull())
    expect(screen.queryByRole('dialog', { name: 'dateTimePicker.title' })).toBeNull()
    await waitFor(() => expect(document.activeElement?.getAttribute('aria-label')).toBe('expiry'))
  })

  it('disables past days and supports calendar keyboard navigation', () => {
    setup()
    const past = screen.getByRole('button', { name: /2030年1月9日/ }) as HTMLButtonElement
    expect(past.disabled).toBe(true)
    const selected = screen.getByRole('button', { name: /2030年1月15日/ })
    selected.focus()
    fireEvent.keyDown(selected, { key: 'ArrowRight' })
    expect(document.activeElement?.getAttribute('aria-label')).toContain('2030年1月16日')
  })
})
