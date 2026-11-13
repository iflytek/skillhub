/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { useState } from 'react'
import { Switch } from './switch'

afterEach(cleanup)

it('exposes its controlled state and lets a form decide when to save', () => {
  const save = vi.fn()
  function Form() {
    const [checked, setChecked] = useState(false)
    return <form onSubmit={event => { event.preventDefault(); save(checked) }}><label htmlFor="enabled">Enabled</label><Switch id="enabled" checked={checked} onCheckedChange={setChecked} /><button type="submit">Save</button></form>
  }
  render(<Form />)
  const toggle = screen.getByRole('switch', { name: 'Enabled' })
  fireEvent.click(toggle)
  expect(toggle.getAttribute('aria-checked')).toBe('true')
  expect(save).not.toHaveBeenCalled()
  fireEvent.click(screen.getByRole('button', { name: 'Save' }))
  expect(save).toHaveBeenCalledWith(true)
})

it('does not change a disabled state or a canceled activation', () => {
  const change = vi.fn()
  const view = render(<Switch aria-label="Enabled" disabled checked onCheckedChange={change} />)
  fireEvent.click(screen.getByRole('switch'))
  expect(change).not.toHaveBeenCalled()
  view.rerender(<Switch aria-label="Enabled" checked onCheckedChange={change} onClick={event => event.preventDefault()} />)
  fireEvent.click(screen.getByRole('switch'))
  expect(change).not.toHaveBeenCalled()
})
