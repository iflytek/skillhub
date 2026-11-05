// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import PresentationEditorRich from './presentation-editor-rich'

const engine = vi.hoisted(() => ({ options: undefined as IOptions | undefined, destroy: vi.fn(), construct: vi.fn(), value: '' }))
vi.mock('react-i18next', () => {
  const t = (key: string) => key
  return { useTranslation: () => ({ t, i18n: { resolvedLanguage: 'zh' } }) }
})
vi.mock('vditor', () => ({ default: class {
  constructor(element: HTMLElement, options: IOptions) {
    engine.construct()
    engine.options = options
    element.innerHTML = '<div class="vditor-toolbar"><button type="button">format</button></div><div contenteditable="true">content</div>'
    queueMicrotask(() => options.after?.())
  }
  destroy() { engine.destroy() }
  focus() {}
  insertMD() {}
  getValue() { return engine.value }
} }))

afterEach(() => { cleanup(); vi.clearAllMocks() })

async function loadAssets() {
  for (const script of document.querySelectorAll('script[src]')) fireEvent.load(script)
  await waitFor(() => expect(engine.construct).toHaveBeenCalled())
}

const props = { value: '# original\n\n  spaces\n', onChange: vi.fn(), onError: vi.fn(), onReady: vi.fn(), onTouched: vi.fn(), onRegister: vi.fn(), onImage: vi.fn() }

describe('PresentationEditorRich adapter', () => {
  it('loads only same-origin emitted assets with cache and remote renderers disabled', async () => {
    render(<PresentationEditorRich {...props} />)
    for (const script of document.querySelectorAll<HTMLScriptElement>('script[src]')) {
      expect(new URL(script.src).origin).toBe(window.location.origin)
      expect(script.src).not.toContain('unpkg')
      expect(script.src).not.toMatch(/^data:/)
    }
    await loadAssets()
    expect(engine.options?.cache?.enable).toBe(false)
    expect(engine.options?.icon).toBeUndefined()
    expect(engine.options?.preview?.theme?.path).toBe('')
    expect(engine.options?.preview?.hljs?.enable).toBe(false)
  })

  it('does not publish normalization changes after initialization or a content-only click', async () => {
    const onChange = vi.fn()
    render(<PresentationEditorRich {...props} onChange={onChange} />)
    await loadAssets()
    engine.options?.input?.('# normalized')
    fireEvent.click(screen.getByText('content'))
    engine.options?.input?.('# normalized')
    expect(onChange).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: 'format' }))
    engine.options?.input?.('**formatted**')
    expect(onChange).toHaveBeenCalledExactlyOnceWith('**formatted**')
  })

  it('flushes a touch-only format action before the delayed native input callback', async () => {
    const reader: { current: (() => string | undefined) | null } = { current: null }
    const onRegister = vi.fn((getter: (() => string | undefined) | null) => { reader.current = getter })
    const onChange = vi.fn()
    render(<PresentationEditorRich {...props} onChange={onChange} onRegister={onRegister} />)
    await loadAssets()
    await waitFor(() => expect(onRegister).toHaveBeenCalled())
    expect(reader.current?.()).toBeUndefined()
    const button = screen.getByRole('button', { name: 'format' })
    button.addEventListener('touchstart', (event) => { event.preventDefault(); event.stopPropagation(); engine.value = '**formatted on iPhone**' })
    fireEvent.touchStart(button)
    expect(onChange).not.toHaveBeenCalled()
    expect(reader.current?.()).toBe('**formatted on iPhone**')
  })

  it('does not mark disabled toolbar actions as edited on pointer down', async () => {
    const reader: { current: (() => string | undefined) | null } = { current: null }
    const onRegister = vi.fn((getter: (() => string | undefined) | null) => { reader.current = getter })
    render(<PresentationEditorRich {...props} onRegister={onRegister} />)
    await loadAssets()
    await waitFor(() => expect(onRegister).toHaveBeenCalled())
    const button = screen.getByRole('button', { name: 'format' }) as HTMLButtonElement
    button.disabled = true
    fireEvent.pointerDown(button)
    expect(reader.current?.()).toBeUndefined()
  })

  it('provides the custom insertion group callback required by Vditor', async () => {
    render(<PresentationEditorRich {...props} />)
    await loadAssets()
    const menu = engine.options?.toolbar?.find((item): item is IMenuItem => typeof item !== 'string' && item.name === 'skillhub-insert')
    expect(menu?.click).toBeTypeOf('function')
    // Vditor invokes custom callbacks before its own submenu toggle; this group has no action.
    expect(() => Reflect.apply(menu!.click!, undefined, [new Event('click'), {}])).not.toThrow()
    expect(menu?.toolbar?.length).toBeGreaterThan(0)
  })

  it('destroys its engine when unmounted', async () => {
    const { unmount } = render(<PresentationEditorRich {...props} />)
    await loadAssets()
    unmount()
    expect(engine.destroy).toHaveBeenCalledOnce()
  })
})
