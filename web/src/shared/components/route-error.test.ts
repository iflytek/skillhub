import { describe, expect, it } from 'vitest'
import * as mod from './route-error'

describe('route-error module', () => {
  it('exports the RouteError component', () => {
    expect(mod.RouteError).toBeTypeOf('function')
  })
})
