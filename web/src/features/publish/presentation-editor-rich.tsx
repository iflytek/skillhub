import { createElement, useEffect, useRef } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { Bold, Italic, List, ListOrdered, Quote, Link, Code2, Image, Undo2, Redo2, Heading, ChevronDown } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import Vditor from 'vditor'
import luteUrl from 'vditor/dist/js/lute/lute.min.js?url&no-inline'
import zhUrl from 'vditor/dist/js/i18n/zh_CN.js?url&no-inline'
import enUrl from 'vditor/dist/js/i18n/en_US.js?url&no-inline'
import koUrl from 'vditor/dist/js/i18n/ko_KR.js?url&no-inline'
import ruUrl from 'vditor/dist/js/i18n/ru_RU.js?url&no-inline'
import 'vditor/dist/index.css'
import './presentation-editor.css'

const scripts = new Map<string, Promise<void>>()
const languages = new Map<string, ITips>()
function loadScript(url: string, id: string): Promise<void> {
  const pending = scripts.get(url)
  if (pending) return pending
  const promise = new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = url
    script.async = true
    script.onload = () => { script.id = id; resolve() }
    script.onerror = () => { script.remove(); scripts.delete(url); reject(new Error('Editor asset failed')) }
    document.head.appendChild(script)
  })
  scripts.set(url, promise)
  return promise
}

interface Props {
  value: string
  onChange: (value: string) => void
  onError: () => void
  onReady: () => void
  onTouched: () => void
  onRegister: (read: (() => string | undefined) | null) => void
  onImage: (insert: (markdown: string) => void) => void
}

// The native editor owns only its host. All mode switching and publishing state stay in React.
export default function PresentationEditorRich({ value, onChange, onError, onReady, onTouched, onRegister, onImage }: Props) {
  const host = useRef<HTMLDivElement>(null)
  const callbacks = useRef({ onChange, onError, onReady, onTouched, onRegister, onImage })
  callbacks.current = { onChange, onError, onReady, onTouched, onRegister, onImage }
  const initialValue = useRef(value)
  initialValue.current = value
  const { i18n, t } = useTranslation()
  const language = i18n.resolvedLanguage?.split('-')[0] ?? 'en'
  const imageLabel = t('publish.editor.insertImage')

  useEffect(() => {
    let cancelled = false
    let instance: Vditor | undefined
    let changedByUser = false
    let ready = false
    const element = host.current
    if (!element) return
    const markChanged = () => { changedByUser = true; callbacks.current.onTouched() }
    const markToolbarChange = (event: Event) => {
      const button = event.target instanceof Element ? event.target.closest('.vditor-toolbar button') : null
      if (button instanceof HTMLButtonElement && !button.disabled && button.getAttribute('aria-disabled') !== 'true') markChanged()
    }
    element.addEventListener('beforeinput', markChanged)
    element.addEventListener('paste', markChanged)
    element.addEventListener('drop', markChanged)
    element.addEventListener('click', markToolbarChange)
    // iPhone Vditor actions use touchstart.preventDefault(), so a compatible click may never follow.
    element.addEventListener('touchstart', markToolbarChange, true)
    element.addEventListener('pointerdown', markToolbarChange, true)
    const icon = (Component: typeof Bold) => renderToStaticMarkup(createElement(Component, { width: 17, height: 17, 'aria-hidden': true }))
    const localeUrl = ({ zh: zhUrl, ko: koUrl, ru: ruUrl } as Record<string, string>)[language] ?? enUrl
    // Preload both assets before construction: Vditor's asynchronous initialization otherwise
    // races React StrictMode cleanup. URLs are Vite-emitted local assets, including subpath bases.
    Promise.all([loadScript(luteUrl, 'vditorLuteScript'), loadScript(localeUrl, `skillhub-editor-language-${language}`).then(() => {
      if (!languages.has(localeUrl)) languages.set(localeUrl, { ...window.VditorI18n })
    })])
      .then(() => {
        if (cancelled) return
        instance = new Vditor(element, {
          value: initialValue.current,
          mode: 'wysiwyg',
          i18n: languages.get(localeUrl),
          icon: undefined,
          cdn: new URL(import.meta.env.BASE_URL, window.location.origin).href.replace(/\/$/, ''),
          _lutePath: luteUrl,
          cache: { enable: false },
          minHeight: 480,
          height: 'auto',
          width: '100%',
          theme: document.documentElement.classList.contains('dark') ? 'dark' : 'classic',
          counter: { enable: false },
          resize: { enable: false },
          hint: { emoji: {}, emojiPath: '', parse: false },
          preview: {
            mode: 'editor',
            hljs: { enable: false },
            theme: { current: '', path: '' },
            markdown: { sanitize: true, codeBlockPreview: false, mathBlockPreview: false, toc: false },
          },
          toolbar: [
            { name: 'headings', icon: icon(Heading) }, '|',
            { name: 'bold', icon: icon(Bold) }, { name: 'italic', icon: icon(Italic) }, '|',
            { name: 'list', icon: icon(List) }, { name: 'quote', icon: icon(Quote) }, { name: 'link', icon: icon(Link) },
            { name: 'skillhub-insert', icon: icon(ChevronDown), tip: t('publish.editor.insertMore'), click: () => { /* Vditor toggles the submenu after the required custom callback. */ }, toolbar: [
              { name: 'ordered-list', icon: icon(ListOrdered) },
              { name: 'code', icon: icon(Code2) },
              { name: 'skillhub-image', icon: icon(Image), tip: imageLabel, click: () => {
                callbacks.current.onImage((markdown) => {
                  if (cancelled || !instance) return
                  markChanged()
                  instance.focus()
                  instance.insertMD(markdown)
                  callbacks.current.onChange(instance.getValue())
                })
              } },
            ] }, '|',
            { name: 'undo', icon: icon(Undo2) }, { name: 'redo', icon: icon(Redo2) },
          ],
          customWysiwygToolbar: (type, toolbar) => {
            // Image insertion goes through the HTTPS-only product dialog.
            if (type === 'image') toolbar.replaceChildren()
          },
          input: (markdown) => {
            if (!cancelled && ready && changedByUser) callbacks.current.onChange(markdown)
          },
          after: () => {
            if (cancelled) return
            ready = true
            callbacks.current.onRegister(() => !cancelled && ready && changedByUser ? instance?.getValue() : undefined)
            element.querySelector('[contenteditable="true"]')?.setAttribute('aria-label', t('publish.editor.visual'))
            callbacks.current.onReady()
          },
        })
      })
      .catch(() => { if (!cancelled) callbacks.current.onError() })
    return () => {
      cancelled = true
      callbacks.current.onRegister(null)
      element.removeEventListener('beforeinput', markChanged)
      element.removeEventListener('paste', markChanged)
      element.removeEventListener('drop', markChanged)
      element.removeEventListener('click', markToolbarChange)
      element.removeEventListener('touchstart', markToolbarChange, true)
      element.removeEventListener('pointerdown', markToolbarChange, true)
      if (instance) instance.destroy()
    }
  }, [language, imageLabel, t])

  return <div ref={host} className="presentation-rich" data-testid="presentation-rich-editor" />
}
