/** @vitest-environment jsdom */
import { createElement } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import * as mod from './create-namespace-dialog'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))

const mutateAsync = vi.fn().mockResolvedValue({ slug: 'team', displayName: 'Team' })

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useCreateNamespace: () => ({ mutateAsync, isPending: false, error: null, reset: vi.fn() }),
}))

vi.mock('@/shared/lib/toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

/**
 * create-namespace-dialog.tsx exports the CreateNamespaceDialog component.
 * The validation helper (buildFieldErrors), slug constants (SLUG_PATTERN,
 * RESERVED_SLUGS, length limits), and FieldErrors type are all
 * module-private. We verify the public export contract here.
 */
describe('create-namespace-dialog module exports', () => {
  it('exports the CreateNamespaceDialog component', () => {
    expect(mod.CreateNamespaceDialog).toBeDefined()
    expect(typeof mod.CreateNamespaceDialog).toBe('function')
  })
})

/**
 * The dialog's client-side slug check must mirror the server's
 * com.iflytek.skillhub.domain.namespace.SlugValidator. Since
 * iflytek/skillhub#196 ("feat: support Unicode characters in skill slugs")
 * that validator accepts Unicode letters, numbers and symbols alongside
 * hyphens, and the publish guide documents that slugs support Unicode.
 *
 * Each case below submits the form, so a slug the server accepts has to reach
 * `mutateAsync` instead of being stopped by `myNamespaces.createSlugPattern`.
 */
describe('CreateNamespaceDialog slug validation mirrors SlugValidator', () => {
  const { CreateNamespaceDialog } = mod

  function submitSlug(slug: string): void {
    render(createElement(
      CreateNamespaceDialog,
      null,
      createElement('button', { type: 'button' }, 'open'),
    ))
    fireEvent.click(screen.getAllByRole('button', { name: 'open' })[0]!)
    fireEvent.change(screen.getByLabelText('myNamespaces.createSlugLabel'), { target: { value: slug } })
    fireEvent.change(screen.getByLabelText('myNamespaces.createDisplayNameLabel'), { target: { value: 'Team' } })
    fireEvent.click(screen.getByRole('button', { name: 'myNamespaces.createSubmit' }))
  }

  beforeEach(() => {
    mutateAsync.mockClear()
  })

  afterEach(cleanup)

  it.each([
    ['chinese', '技能包'],
    ['japanese', 'スキル'],
    ['korean', '스킬'],
    ['emoji', '🎯-target'],
    ['rocket emoji', '🚀-rocket'],
    ['mixed unicode', 'my-技能-v2'],
    ['accented latin', 'café'],
    ['cyrillic', 'привет'],
  ])('submits %s slugs that SlugValidator accepts', (_label, slug) => {
    submitSlug(slug)

    expect(screen.queryByText('myNamespaces.createSlugPattern')).toBeNull()
    expect(mutateAsync).toHaveBeenCalledWith(expect.objectContaining({ slug }))
  })

  it.each([
    ['leading hyphen', '-abc', 'myNamespaces.createSlugPattern'],
    ['trailing hyphen', 'abc-', 'myNamespaces.createSlugPattern'],
    ['underscore', 'my_namespace', 'myNamespaces.createSlugPattern'],
    ['dot', 'my.namespace', 'myNamespaces.createSlugPattern'],
    ['double hyphen', 'ab--cd', 'myNamespaces.createSlugDoubleHyphen'],
    ['reserved word', 'admin', 'myNamespaces.createSlugReserved'],
  ])('still blocks %s before submitting', (_label, slug, errorKey) => {
    submitSlug(slug)

    expect(screen.getByText(errorKey)).toBeTruthy()
    expect(mutateAsync).not.toHaveBeenCalled()
  })
})