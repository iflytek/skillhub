/** @vitest-environment jsdom */
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render } from '@testing-library/react'
const mocks = vi.hoisted(() => ({ navigate: vi.fn(), queue: 'sync' as string | undefined }))
vi.mock('@tanstack/react-router', () => ({ useParams: () => ({ slug: 'design' }), useSearch: () => ({ queue: mocks.queue }), useNavigate: () => mocks.navigate }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
import { NamespaceReviewsPage } from './namespace-reviews'
afterEach(() => { cleanup(); vi.clearAllMocks() })
describe('namespace review compatibility entry', () => {
  it('opens the shared queue with the namespace and legacy sync filter', () => {
    render(<NamespaceReviewsPage />)
    expect(mocks.navigate).toHaveBeenCalledWith({ to: '/dashboard/reviews', search: { namespace: 'design', originType: 'GITHUB_SYNC' }, replace: true })
  })
  it('does not create another namespace-specific review list', () => {
    mocks.queue = undefined
    const view = render(<NamespaceReviewsPage />)
    expect(view.container.querySelector('table')).toBeNull()
    expect(mocks.navigate).toHaveBeenCalledWith({ to: '/dashboard/reviews', search: { namespace: 'design', originType: undefined }, replace: true })
  })
})
