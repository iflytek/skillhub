// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { QuickStartSection } from './quick-start'
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
afterEach(cleanup)
describe('quick-start heading composition', () => {
  it('keeps existing titles by default and can reuse commands inside a labelled disclosure', () => {
    const { rerender } = render(<QuickStartSection ns="home" />)
    expect(screen.getByRole('heading', { name: 'home.quickStart.title' })).toBeTruthy()
    rerender(<QuickStartSection ns="home" hideHeading />)
    expect(screen.queryByRole('heading', { name: 'home.quickStart.title' })).toBeNull()
    expect(screen.getByText('home.quickStart.steps.configureEnv.title')).toBeTruthy()
    expect(screen.getByText('home.quickStart.steps.installSkills.title')).toBeTruthy()
    expect(screen.getByText('home.quickStart.steps.publishSkills.title')).toBeTruthy()
    expect(screen.getAllByRole('button', { name: 'copyButton.copy' })).toHaveLength(3)
  })
})
