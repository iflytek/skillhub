/** @vitest-environment jsdom */
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/client', () => ({ buildApiUrl: (url: string) => `/proxy${url}` }))
import { SkillAvatar } from './skill-avatar'

describe('SkillAvatar', () => {
  afterEach(cleanup)
  it('shows the skill initial when no image is approved', () => {
    render(<SkillAvatar name="Weather" />)
    expect(screen.getByText('W')).toBeTruthy()
  })

  it.each([['  weather', 'W'], ['会议纪要', '会'], ['', '?']])('uses the first visible character for %s', (name, initial) => {
    render(<SkillAvatar name={name} />)
    expect(screen.getByText(initial)).toBeTruthy()
  })

  it('uses varied stable identity colors even when callers do not provide a seed', () => {
    const names = Array.from({ length: 24 }, (_, index) => `Skill ${index}`)
    const view = render(<>{names.map(name => <SkillAvatar key={name} name={name} />)}</>)
    const colors = Array.from(view.container.children, child => child.className)
    expect(new Set(colors).size).toBeGreaterThanOrEqual(6)
    expect(colors.every(color => !color.includes('bg-primary/10'))).toBe(true)
    view.rerender(<>{names.map(name => <SkillAvatar key={name} name={name} />)}</>)
    expect(Array.from(view.container.children, child => child.className)).toEqual(colors)
  })

  it('falls back to the initial if an approved image is unavailable', () => {
    const { container } = render(<SkillAvatar name="Weather" url="/avatar" />)
    const image = container.querySelector('img')!
    expect(image.getAttribute('src')).toBe('/proxy/avatar')
    expect(screen.queryByText('W')).toBeNull()
    fireEvent.error(image)
    expect(screen.getByText('W')).toBeTruthy()
  })

  it('keeps the same fallback palette when a skill is renamed or remounted', () => {
    const first = render(<SkillAvatar name="Weather" seed="team/weather" />)
    const palette = first.container.firstElementChild!.className
    first.rerender(<SkillAvatar name="Forecast" seed="team/weather" />)
    expect(first.container.firstElementChild!.className).toBe(palette)
    expect(screen.getByText('F')).toBeTruthy()
    first.unmount()
    const remount = render(<SkillAvatar name="Forecast" seed="team/weather" />)
    expect(remount.container.firstElementChild!.className).toBe(palette)
  })

  it('uses varied stable palettes for distinct skill coordinates', () => {
    const view = render(<><SkillAvatar name="A" seed="team/a" /><SkillAvatar name="B" seed="team/b" /><SkillAvatar name="C" seed="team/c" /></>)
    expect(new Set(Array.from(view.container.children, child => child.className)).size).toBe(3)
  })

  it('preserves approved images and returns to the same seeded palette after an image failure', () => {
    const view = render(<SkillAvatar name="Weather" seed="team/weather" />)
    const fallback = view.container.firstElementChild!.className
    view.rerender(<SkillAvatar name="Weather" seed="team/weather" url="/avatar" />)
    const image = view.container.querySelector('img')!
    expect(image.getAttribute('src')).toBe('/proxy/avatar')
    expect(view.container.firstElementChild!.className).not.toBe(fallback)
    fireEvent.error(image)
    expect(view.container.querySelector('img')).toBeNull()
    expect(view.container.firstElementChild!.className).toBe(fallback)
  })
})
