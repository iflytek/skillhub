/** @vitest-environment jsdom */

import { afterEach, describe, expect, it } from 'vitest'
import { getPortalContainer, PORTAL_ROOT_ID } from './portal-container'

describe('getPortalContainer', () => {
  afterEach(() => {
    document.getElementById(PORTAL_ROOT_ID)?.remove()
  })

  it('returns undefined when the host is missing', () => {
    expect(getPortalContainer()).toBeUndefined()
  })

  it('returns the dedicated portal host when present', () => {
    const host = document.createElement('div')
    host.id = PORTAL_ROOT_ID
    document.body.appendChild(host)
    expect(getPortalContainer()).toBe(host)
  })
})
