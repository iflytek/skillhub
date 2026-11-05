// @vitest-environment jsdom
import { useEffect, useRef, useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { PresentationEditor, requiresSourceEditing } from './presentation-editor'

vi.mock('@tanstack/react-router', () => ({ useBlocker: () => ({ status: 'idle' }) }))
vi.mock('react-i18next', () => ({ useTranslation: () => ({ t: (key: string) => key }) }))
vi.mock('./presentation-editor-rich', () => ({ default: function FakeRichEditor({ value, onChange, onError, onImage, onRegister, onTouched }: { value: string; onChange: (value: string) => void; onError: () => void; onImage: (insert: (value: string) => void) => void; onRegister: (read: (() => string | undefined) | null) => void; onTouched: () => void }) {
  const current = useRef(value)
  const edited = useRef(false)
  useEffect(() => {
    onRegister(() => edited.current ? current.current : undefined)
    return () => onRegister(null)
  }, [onRegister])
  return <div>
    <textarea aria-label="rich-editor" defaultValue={value} onChange={(event) => { edited.current = true; current.current = event.target.value; onChange(event.target.value) }} />
    <button onClick={onError}>engine-error</button>
    <button onClick={() => { edited.current = true; current.current = 'last pending input'; onTouched() }}>delayed-engine-input</button>
    <button onClick={() => onImage((markdown) => onChange(value + markdown))}>image-action</button>
  </div>
} }))

afterEach(cleanup)
const defaults = { open: true, value: '# 原文\n\n保持  空格\n', onApply: vi.fn(), onCancel: vi.fn() }

function source() { fireEvent.click(screen.getByRole('tab', { name: 'publish.editor.source' })); return screen.getByRole('textbox', { name: 'publish.editor.markdownLabel' }) }

describe('PresentationEditor', () => {
  it('does not mount an editor when closed', () => {
    render(<PresentationEditor {...defaults} open={false} />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('preserves exact source during a no-edit source/preview round trip', () => {
    const apply = vi.fn()
    render(<PresentationEditor {...defaults} onApply={apply} />)
    expect((source() as HTMLTextAreaElement).value).toBe(defaults.value)
    fireEvent.click(screen.getByRole('tab', { name: 'publish.editor.preview' }))
    fireEvent.click(screen.getByRole('tab', { name: 'publish.editor.source' }))
    fireEvent.click(screen.getByRole('button', { name: 'publish.editor.apply' }))
    expect(apply).toHaveBeenCalledExactlyOnceWith(defaults.value)
  })

  it('only applies source changes when explicitly completed', () => {
    const apply = vi.fn()
    render(<PresentationEditor {...defaults} onApply={apply} />)
    fireEvent.change(source(), { target: { value: '新的概述\n\n```custom-lang\n<raw>\n```\n' } })
    expect(apply).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.editor.apply' }))
    expect(apply).toHaveBeenCalledWith('新的概述\n\n```custom-lang\n<raw>\n```\n')
  })

  it('requires confirmation before abandoning changes and can continue editing', () => {
    const cancel = vi.fn()
    render(<PresentationEditor {...defaults} onCancel={cancel} />)
    fireEvent.change(source(), { target: { value: '修改后' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'publish.editor.cancel' })[0])
    expect(cancel).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'publish.editor.keepEditing' }))
    expect((screen.getByRole('textbox', { name: 'publish.editor.markdownLabel' }) as HTMLTextAreaElement).value).toBe('修改后')
    fireEvent.click(screen.getAllByRole('button', { name: 'publish.editor.cancel' })[0])
    fireEvent.click(screen.getByRole('button', { name: 'publish.editor.discard' }))
    expect(cancel).toHaveBeenCalledOnce()
  })

  it('blocks over-limit completion without truncating the draft', () => {
    const apply = vi.fn()
    render(<PresentationEditor {...defaults} maxLength={5} onApply={apply} />)
    fireEvent.change(source(), { target: { value: '123456' } })
    expect(screen.getByRole('alert').textContent).toBe('publish.editor.tooLong')
    expect((screen.getByRole('button', { name: 'publish.editor.apply' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('textbox', { name: 'publish.editor.markdownLabel' }) as HTMLTextAreaElement).value).toBe('123456')
    expect(apply).not.toHaveBeenCalled()
  })

  it('retains unsupported HTML verbatim and starts in source mode', () => {
    const value = '<details><summary>内容</summary>\n\n未修改\n</details>'
    render(<PresentationEditor {...defaults} value={value} />)
    expect((screen.getByRole('tab', { name: 'publish.editor.visual' }) as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByRole('textbox', { name: 'publish.editor.markdownLabel' }) as HTMLTextAreaElement).value).toBe(value)
  })

  it('recovers to the source if the visual engine fails', async () => {
    render(<PresentationEditor {...defaults} />)
    fireEvent.click(await screen.findByRole('button', { name: 'engine-error' }))
    expect((screen.getByRole('textbox', { name: 'publish.editor.markdownLabel' }) as HTMLTextAreaElement).value).toBe(defaults.value)
    expect(screen.getByText('publish.editor.loadFailed')).toBeTruthy()
  })

  it('rejects non-HTTPS image insertion without altering source', async () => {
    const apply = vi.fn()
    render(<PresentationEditor {...defaults} onApply={apply} />)
    fireEvent.click(await screen.findByRole('button', { name: 'image-action' }))
    fireEvent.change(screen.getByLabelText('publish.editor.imageUrl'), { target: { value: 'javascript:alert(1)' } })
    fireEvent.click(screen.getByRole('button', { name: 'publish.editor.insert' }))
    expect(screen.getByRole('alert').textContent).toBe('publish.editor.invalidImageUrl')
    expect(apply).not.toHaveBeenCalled()
  })

  it('flushes the latest native value before completion even before the debounced input callback', async () => {
    const apply = vi.fn()
    render(<PresentationEditor {...defaults} onApply={apply} />)
    fireEvent.click(await screen.findByRole('button', { name: 'delayed-engine-input' }))
    fireEvent.click(screen.getByRole('button', { name: 'publish.editor.apply' }))
    expect(apply).toHaveBeenCalledExactlyOnceWith('last pending input')
  })

  it('flushes pending native input before changing modes', async () => {
    render(<PresentationEditor {...defaults} />)
    fireEvent.click(await screen.findByRole('button', { name: 'delayed-engine-input' }))
    expect((source() as HTMLTextAreaElement).value).toBe('last pending input')
  })

  it('confirms cancellation of native edits whose callback is still pending', async () => {
    const cancel = vi.fn()
    render(<PresentationEditor {...defaults} onCancel={cancel} />)
    fireEvent.click(await screen.findByRole('button', { name: 'delayed-engine-input' }))
    fireEvent.click(screen.getAllByRole('button', { name: 'publish.editor.cancel' })[0])
    expect(screen.getByRole('button', { name: 'publish.editor.discard' })).toBeTruthy()
    expect(cancel).not.toHaveBeenCalled()
  })

  it('revalidates the actual native input length before applying', async () => {
    const apply = vi.fn()
    render(<PresentationEditor {...defaults} value="" maxLength={5} onApply={apply} />)
    fireEvent.click(await screen.findByRole('button', { name: 'delayed-engine-input' }))
    fireEvent.click(screen.getByRole('button', { name: 'publish.editor.apply' }))
    expect(apply).not.toHaveBeenCalled()
    expect(screen.getByRole('alert').textContent).toBe('publish.editor.tooLong')
  })

  it('restores focus to its trigger after confirming discard through a nested dialog', async () => {
    function Harness() {
      const [open, setOpen] = useState(false)
      return <><button onClick={() => setOpen(true)}>edit-overview-trigger</button><PresentationEditor {...defaults} open={open} onCancel={() => setOpen(false)} /></>
    }
    render(<Harness />)
    const trigger = screen.getByRole('button', { name: 'edit-overview-trigger' })
    trigger.focus()
    fireEvent.click(trigger)
    fireEvent.change(source(), { target: { value: 'changed' } })
    fireEvent.click(screen.getAllByRole('button', { name: 'publish.editor.cancel' })[0])
    fireEvent.click(screen.getByRole('button', { name: 'publish.editor.discard' }))
    await waitFor(() => expect(document.activeElement).toBe(trigger))
  })

  it('distinguishes supported fences and plain Unicode text from raw HTML', () => {
    expect(requiresSourceEditing('中文说明\n\n```unknown-language\nhello\n```')).toBe(false)
    expect(requiresSourceEditing('<!-- preserve -->')).toBe(true)
  })
})
