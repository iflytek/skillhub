import { describe, expect, it } from 'vitest'
import * as mod from './upload-zone'

/**
 * upload-zone.tsx exports the UploadZone component. It is a stateless
 * dropzone wrapper with no exported constants, validation logic, or
 * helper functions.
 *
 * We verify the export contract so downstream consumers break fast if
 * the module shape changes.
 */
describe('upload-zone module exports', () => {
  it('exports the UploadZone component', () => {
    expect(mod.UploadZone).toBeDefined()
    expect(typeof mod.UploadZone).toBe('function')
  })
})
