/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createElement } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { UploadZone } from './upload-zone'
import zh from '@/i18n/locales/zh.json'

vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key === 'upload.formatHint' ? zh.upload.formatHint : key }) }))

const zip = (name = 'meeting-notes.zip') => new File(['package'], name, { type: 'application/zip' })
function transfer(files: File[]) {
  return {
    files,
    items: files.map(file => ({ kind: 'file', type: file.type, getAsFile: () => file })),
    types: ['Files'],
  }
}

async function drop(element: HTMLElement, files: File[]) {
  await act(async () => { fireEvent.drop(element, { dataTransfer: transfer(files) }) })
}

function directorySupport() {
  const previous = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'webkitdirectory')
  Object.defineProperty(HTMLInputElement.prototype, 'webkitdirectory', { configurable: true, writable: true, value: false })
  return () => {
    if (previous) Object.defineProperty(HTMLInputElement.prototype, 'webkitdirectory', previous)
    else delete (HTMLInputElement.prototype as { webkitdirectory?: boolean }).webkitdirectory
  }
}

describe('UploadZone', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

  it('keeps the shared ZIP hint generic for Suite Bundle and allows caller-specific requirements', () => {
    const view = render(createElement(UploadZone, { onFileSelect: vi.fn() }))
    expect(screen.getByText(zh.upload.formatHint)).toBeTruthy()
    expect(zh.upload.formatHint).not.toContain('SKILL.md')
    expect(zh.upload.formatHint).not.toContain('SUITE.yaml')
    view.rerender(createElement(UploadZone, { onFileSelect: vi.fn(), formatHint: 'Bundle 根目录需包含 SUITE.yaml' }))
    expect(screen.getByText('Bundle 根目录需包含 SUITE.yaml')).toBeTruthy()
    expect(screen.queryByText(zh.upload.formatHint)).toBeNull()
  })

  it('accepts one ZIP using the real dropzone and replaces the large picker with its controlled file row', async () => {
    const onFileSelect = vi.fn()
    const file = zip()
    const view = render(createElement(UploadZone, { onFileSelect }))
    await drop(screen.getByRole('button', { name: 'upload.dragHint' }), [file])
    expect(onFileSelect).toHaveBeenCalledExactlyOnceWith(file)
    view.rerender(createElement(UploadZone, { onFileSelect, selectedFile: file, previewStatus: 'ready' }))
    expect(screen.queryByText('upload.dragHint')).toBeNull()
    expect(screen.getByText(file.name)).toBeTruthy()
    expect(screen.getByText('publish.packageParsed')).toBeTruthy()
  })

  it.each([
    ['two ZIPs', [zip('one.zip'), zip('two.zip')], 'upload.errors.singleZip'],
    ['a ZIP and a text file', [zip(), new File(['x'], 'notes.txt', { type: 'text/plain' })], 'upload.errors.singleZip'],
    ['a misleading MIME type', [new File(['x'], 'notes.txt', { type: 'application/zip' })], 'upload.errors.zipRequired'],
  ])('rejects %s without replacing the existing package', async (_name, files, expectedError) => {
    const onFileSelect = vi.fn()
    const existing = zip('existing.zip')
    render(createElement(UploadZone, { onFileSelect, selectedFile: existing, previewStatus: 'ready' }))
    await drop(screen.getByText(existing.name), files)
    expect(onFileSelect).not.toHaveBeenCalled()
    expect(screen.getByRole('alert').textContent).toBe(expectedError)
    expect(screen.getByText(existing.name)).toBeTruthy()
  })

  it('refuses a dropped directory before recursively reading its entries', async () => {
    const onFileSelect = vi.fn()
    const getAsFile = vi.fn()
    render(createElement(UploadZone, { onFileSelect }))
    const dataTransfer = { types: ['Files'], files: [], items: [{ kind: 'file', webkitGetAsEntry: () => ({ isDirectory: true }), getAsFile }] }
    await act(async () => { fireEvent.dragEnter(screen.getByRole('button', { name: 'upload.dragHint' }), { dataTransfer }) })
    expect(screen.getByText('upload.dropHint')).toBeTruthy()
    await act(async () => { fireEvent.drop(screen.getByRole('button', { name: 'upload.dragHint' }), { dataTransfer }) })
    expect(screen.queryByText('upload.dropHint')).toBeNull()
    expect(screen.getByRole('alert').textContent).toBe('upload.errors.folderDropUnsupported')
    expect(onFileSelect).not.toHaveBeenCalled()
    expect(getAsFile).not.toHaveBeenCalled()
  })

  it('opens a single native picker from either the full-area button or the explicit ZIP button', () => {
    const click = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {})
    render(createElement(UploadZone, { onFileSelect: vi.fn() }))
    const surface = screen.getByRole('button', { name: 'upload.dragHint' })
    expect(surface.tagName).toBe('BUTTON')
    surface.focus()
    expect(document.activeElement).toBe(surface)
    // detail=0 is the click emitted by native keyboard button activation.
    fireEvent.click(surface, { detail: 0 })
    expect(click).toHaveBeenCalledTimes(1)
    click.mockClear()
    fireEvent.click(screen.getByRole('button', { name: 'upload.selectZip' }))
    expect(click).toHaveBeenCalledTimes(1)
    expect(surface.querySelector('button')).toBeNull()
  })

  it('keeps the current file when the picker is cancelled and accepts the same ZIP again', async () => {
    const onFileSelect = vi.fn()
    const file = zip()
    const view = render(createElement(UploadZone, { onFileSelect, selectedFile: file, previewStatus: 'ready' }))
    const input = view.container.querySelector('input[accept]') as HTMLInputElement
    vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {})
    fireEvent.click(screen.getByRole('button', { name: 'publish.replacePackage' }))
    expect(onFileSelect).not.toHaveBeenCalled()
    expect(screen.getByText(file.name)).toBeTruthy()
    await act(async () => { fireEvent.change(input, { target: { files: [file] } }) })
    await waitFor(() => expect(onFileSelect).toHaveBeenCalledTimes(1))
    await act(async () => { fireEvent.change(input, { target: { files: [file] } }) })
    await waitFor(() => expect(onFileSelect).toHaveBeenCalledTimes(2))
  })

  it('shows a busy reading state with no fabricated numeric percentage', () => {
    render(createElement(UploadZone, { onFileSelect: vi.fn(), selectedFile: zip(), previewStatus: 'reading' }))
    expect(screen.getByText('publish.readingPackage')).toBeTruthy()
    expect(screen.getByRole('progressbar').getAttribute('aria-valuenow')).toBeNull()
    expect(screen.getByRole('button', { name: 'publish.replacePackage' }).hasAttribute('disabled')).toBe(false)
  })

  it('exposes an actionable preview failure and the public replacement focus target', () => {
    render(createElement(UploadZone, { onFileSelect: vi.fn(), selectedFile: zip(), previewStatus: 'error', previewError: 'preview failed', replaceActionId: 'replace-package' }))
    expect(screen.getByRole('alert').textContent).toBe('preview failed')
    expect(screen.queryByText('publish.packagePreviewFailed')).toBeNull()
    const replace = document.getElementById('replace-package')!
    replace.focus()
    expect(document.activeElement).toBe(replace)
  })

  it('disables every picker and ignores file input changes and drops while unavailable', async () => {
    const restore = directorySupport()
    try {
      const onFileSelect = vi.fn(), onFolderSelect = vi.fn(), onRemove = vi.fn()
      const view = render(createElement(UploadZone, { onFileSelect, onFolderSelect, onRemove, selectedFile: zip(), disabled: true }))
      for (const button of screen.getAllByRole('button')) expect(button.hasAttribute('disabled')).toBe(true)
      for (const input of view.container.querySelectorAll('input')) expect(input.disabled).toBe(true)
      for (const input of view.container.querySelectorAll('input')) await act(async () => { fireEvent.change(input, { target: { files: [zip()] } }) })
      await drop(screen.getByText('meeting-notes.zip'), [zip('new.zip')])
      expect(onFileSelect).not.toHaveBeenCalled()
      expect(onFolderSelect).not.toHaveBeenCalled()
      expect(onRemove).not.toHaveBeenCalled()
    } finally { restore() }
  })

  it('shows a packaging state while keeping the original package and actions disabled', () => {
    render(createElement(UploadZone, { onFileSelect: vi.fn(), selectedFile: zip(), isPackaging: true }))
    expect(screen.getByText('meeting-notes.zip')).toBeTruthy()
    expect(screen.getByText('upload.packaging')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'publish.replacePackage' }).hasAttribute('disabled')).toBe(true)
  })

  it('hides directory selection when it is unsupported', () => {
    render(createElement(UploadZone, { onFileSelect: vi.fn(), onFolderSelect: vi.fn() }))
    expect(screen.queryByRole('button', { name: 'upload.folderHint' })).toBeNull()
  })

  it('selects folders through a separate native input, resets it, and reports an empty selection', async () => {
    const restore = directorySupport()
    try {
      const onFileSelect = vi.fn(), onFolderSelect = vi.fn()
      const view = render(createElement(UploadZone, { onFileSelect, onFolderSelect, folderAnalyticsId: 'folder-select', pickerAnalyticsId: 'folder-open' }))
      const folderButton = await screen.findByRole('button', { name: 'upload.folderHint' })
      const click = vi.spyOn(HTMLInputElement.prototype, 'click').mockImplementation(() => {})
      fireEvent.click(folderButton)
      expect(click).toHaveBeenCalledTimes(1)
      expect(folderButton.getAttribute('data-analytics-id')).toBe('folder-open')
      const input = view.container.querySelector('input[data-analytics-id="folder-select"]') as HTMLInputElement
      expect(input.hasAttribute('webkitdirectory')).toBe(true)
      const file = new File(['x'], 'SKILL.md', { type: 'text/markdown' })
      fireEvent.change(input, { target: { files: [file] } })
      expect(onFolderSelect).toHaveBeenCalledExactlyOnceWith([file])
      expect(onFileSelect).not.toHaveBeenCalled()
      expect(input.value).toBe('')
      fireEvent.change(input, { target: { files: [] } })
      expect(screen.getByRole('alert').textContent).toBe('upload.errors.emptySelection')
      expect(onFolderSelect).toHaveBeenCalledTimes(1)
    } finally { restore() }
  })

  it.each(['unmount', 'disable'])('ignores a delayed native file handle after %s', async (change) => {
    vi.stubGlobal('isSecureContext', true)
    const onFileSelect = vi.fn()
    const file = zip()
    let release!: (handle: { getFile: () => Promise<File> }) => void
    const handle = new Promise<{ getFile: () => Promise<File> }>(resolve => { release = resolve })
    const view = render(createElement(UploadZone, { onFileSelect }))
    fireEvent.drop(screen.getByRole('button', { name: 'upload.dragHint' }), {
      dataTransfer: { types: ['Files'], files: [file], items: [{ kind: 'file', getAsFileSystemHandle: () => handle, getAsFile: () => file }] },
    })
    if (change === 'unmount') view.unmount()
    else view.rerender(createElement(UploadZone, { onFileSelect, disabled: true }))
    await act(async () => { release({ getFile: async () => file }); await handle })
    expect(onFileSelect).not.toHaveBeenCalled()
  })

  it('clears a rejected replacement error only after a new valid selection', async () => {
    const onFileSelect = vi.fn()
    render(createElement(UploadZone, { onFileSelect, selectedFile: zip() }))
    await drop(screen.getByText('meeting-notes.zip'), [new File(['x'], 'notes.txt')])
    expect(screen.getByRole('alert')).toBeTruthy()
    await drop(screen.getByText('meeting-notes.zip'), [zip('new.zip')])
    expect(screen.queryByRole('alert')).toBeNull()
    expect(onFileSelect).toHaveBeenCalledTimes(1)
  })
})
