// @vitest-environment jsdom
import { createElement } from 'react'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('@/api/client', () => ({
  fetchJson: vi.fn(),
  getCsrfHeaders: (headers: Record<string, string> = {}) => ({ ...headers, 'X-XSRF-TOKEN': 'test-csrf' }),
}))

import { fetchJson } from '@/api/client'
import { DeviceAuthPage } from './device'

afterEach(cleanup)
beforeEach(() => vi.mocked(fetchJson).mockReset())

function enterCode() {
  const fields = screen.getAllByRole('textbox')
  fireEvent.change(fields[0]!, { target: { value: 'abcd' } })
  fireEvent.change(fields[1]!, { target: { value: '1234' } })
}

describe('DeviceAuthPage', () => {
  it('replaces the authorization form with a focused completion screen', async () => {
    vi.mocked(fetchJson).mockResolvedValue(undefined)
    render(createElement(DeviceAuthPage))
    expect(document.activeElement).toBe(screen.getAllByRole('textbox')[0])
    enterCode()
    fireEvent.click(screen.getByRole('button', { name: 'device.submit' }))
    const heading = await screen.findByRole('heading', { name: 'device.authorizedTitle' })
    expect(screen.getByText('device.success')).toBeTruthy()
    expect(screen.queryByRole('textbox')).toBeNull()
    expect(screen.queryByRole('button', { name: 'device.submit' })).toBeNull()
    expect(screen.queryByText('device.codeHint')).toBeNull()
    expect(screen.queryByText('device.notice')).toBeNull()
    await waitFor(() => expect(document.activeElement).toBe(heading))
    expect(fetchJson).toHaveBeenCalledExactlyOnceWith('/api/v1/device/authorize', {
      method: 'POST', headers: { 'X-XSRF-TOKEN': 'test-csrf', 'Content-Type': 'application/json' },
      body: JSON.stringify({ userCode: 'ABCD-1234' }),
    })
  })

  it('retains the entered code after a failure and allows retry', async () => {
    vi.mocked(fetchJson).mockRejectedValueOnce(new Error('The code expired')).mockResolvedValueOnce(undefined)
    render(createElement(DeviceAuthPage))
    enterCode()
    fireEvent.click(screen.getByRole('button', { name: 'device.submit' }))
    expect(await screen.findByRole('alert')).toHaveProperty('textContent', 'The code expired')
    expect(screen.queryByRole('heading', { name: 'device.authorizedTitle' })).toBeNull()
    const fields = screen.getAllByRole('textbox')
    expect(fields[0]).toHaveProperty('value', 'ABCD')
    expect(fields[1]).toHaveProperty('value', '1234')
    fireEvent.click(screen.getByRole('button', { name: 'device.submit' }))
    expect(await screen.findByRole('heading', { name: 'device.authorizedTitle' })).toBeTruthy()
    expect(fetchJson).toHaveBeenCalledTimes(2)
  })

  it('prevents duplicate submissions while authorization is pending', async () => {
    let complete!: () => void
    vi.mocked(fetchJson).mockReturnValue(new Promise<void>(resolve => { complete = resolve }))
    const { container } = render(createElement(DeviceAuthPage))
    enterCode()
    fireEvent.click(screen.getByRole('button', { name: 'device.submit' }))
    expect(screen.getByRole('button', { name: 'device.submitting' })).toHaveProperty('disabled', true)
    for (const input of screen.getAllByRole('textbox')) expect(input).toHaveProperty('disabled', true)
    fireEvent.submit(container.querySelector('form')!)
    expect(fetchJson).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('heading', { name: 'device.authorizedTitle' })).toBeNull()
    await act(async () => complete())
    expect(screen.getByRole('heading', { name: 'device.authorizedTitle' })).toBeTruthy()
  })

  it('rejects an incomplete code without a request or a success screen', () => {
    const { container } = render(createElement(DeviceAuthPage))
    fireEvent.submit(container.querySelector('form')!)
    expect(screen.getByRole('alert').textContent).toBe('device.incompleteCode')
    expect(fetchJson).not.toHaveBeenCalled()
    expect(screen.getAllByRole('textbox')).toHaveLength(2)
    expect(screen.queryByRole('heading', { name: 'device.authorizedTitle' })).toBeNull()
  })
})
