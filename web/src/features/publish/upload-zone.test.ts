/** @vitest-environment jsdom */

import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { createElement } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import * as mod from './upload-zone'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('react-dropzone', () => ({
  useDropzone: () => ({
    getRootProps: () => ({}),
    getInputProps: () => ({}),
    isDragActive: false,
  }),
}))

/**
 * upload-zone.tsx exports the UploadZone component. It is a stateless
 * dropzone wrapper with no exported constants, validation logic, or
 * helper functions.
 *
 * We verify the export contract so downstream consumers break fast if
 * the module shape changes.
 */
describe('upload-zone module exports', () => {
  afterEach(() => cleanup())

  it('exports the UploadZone component', () => {
    expect(mod.UploadZone).toBeDefined()
    expect(typeof mod.UploadZone).toBe('function')
  })

  it('hides directory selection when the browser does not expose a directory picker', () => {
    render(createElement(mod.UploadZone, { onFileSelect: vi.fn(), onFolderSelect: vi.fn() }))
    expect(screen.queryByRole('button', { name: 'upload.folderHint' })).toBeNull()
  })

  it('shows directory selection only when the browser supports it', async () => {
    const previous = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'webkitdirectory')
    Object.defineProperty(HTMLInputElement.prototype, 'webkitdirectory', {
      configurable: true,
      writable: true,
      value: false,
    })
    try {
      render(createElement(mod.UploadZone, { onFileSelect: vi.fn(), onFolderSelect: vi.fn() }))
      await waitFor(() => expect(
        screen.getByRole('button', { name: 'upload.folderHint' })
      ).not.toBeNull())
    } finally {
      if (previous) Object.defineProperty(HTMLInputElement.prototype, 'webkitdirectory', previous)
      else delete (HTMLInputElement.prototype as { webkitdirectory?: boolean }).webkitdirectory
    }
  })
})
