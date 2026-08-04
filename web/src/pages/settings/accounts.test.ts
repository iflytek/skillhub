/** @vitest-environment jsdom */

import { render, screen } from '@testing-library/react'
import { createElement } from 'react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

import { AccountSettingsPage } from './accounts'

describe('AccountSettingsPage', () => {
  it('shows the temporary isolation notice without legacy merge controls', () => {
    const { container } = render(createElement(AccountSettingsPage))

    expect(screen.getByText('accounts.unavailableTitle')).toBeTruthy()
    expect(screen.getByText('accounts.unavailableDescription')).toBeTruthy()
    expect(screen.getByText('accounts.unavailableOperatorAction')).toBeTruthy()
    expect(container.querySelector('form')).toBeNull()
    expect(container.querySelector('input')).toBeNull()
    expect(container.querySelector('button')).toBeNull()
  })
})
