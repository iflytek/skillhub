import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children }: { children: unknown }) => children,
  useNavigate: () => vi.fn(),
  useSearch: () => ({ returnTo: '' }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/features/auth/login-button', () => ({
  LoginButton: () => null,
}))

vi.mock('@/features/auth/use-local-auth', () => ({
  useLocalRegister: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    error: null,
  }),
}))

vi.mock('@/shared/ui/button', () => ({
  Button: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
  CardContent: ({ children }: { children: unknown }) => children,
  CardDescription: ({ children }: { children: unknown }) => children,
  CardHeader: ({ children }: { children: unknown }) => children,
  CardTitle: ({ children }: { children: unknown }) => children,
}))

vi.mock('@/shared/ui/input', () => ({
  Input: () => null,
}))

vi.mock('@/shared/ui/tabs', () => ({
  Tabs: ({ children }: { children: unknown }) => children,
  TabsContent: ({ children }: { children: unknown }) => children,
  TabsList: ({ children }: { children: unknown }) => children,
  TabsTrigger: ({ children }: { children: unknown }) => children,
}))

import { renderToStaticMarkup } from 'react-dom/server'
import { RegisterPage } from './register'

describe('RegisterPage', () => {
  it('exports a named component function', () => {
    expect(typeof RegisterPage).toBe('function')
  })

  it('renders the registration title and form fields', () => {
    const html = renderToStaticMarkup(<RegisterPage />)

    expect(html).toContain('register.title')
    expect(html).toContain('register.subtitle')
    expect(html).toContain('register.submit')
  })
})
