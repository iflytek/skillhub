import { describe, expect, it } from 'vitest'
import { toRouterPath } from './base-path'

describe('toRouterPath', () => {
  it('removes the deployment base path while preserving search and hash', () => {
    expect(toRouterPath('/skillhub/search', '?q=java', '#results', '/skillhub/')).toBe('/search?q=java#results')
  })

  it('keeps root deployments and unrelated paths unchanged', () => {
    expect(toRouterPath('/search', '?q=java', '#results', '/')).toBe('/search?q=java#results')
    expect(toRouterPath('/skillhub-admin/search', '', '', '/skillhub/')).toBe('/skillhub-admin/search')
  })
})
