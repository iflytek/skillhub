export const TOKEN_PRESETS = {
  readonly: ['skill:read'],
  candidate: ['skill:read', 'skill:submit'],
  private: ['skill:read', 'skill:submit', 'skill:publish-private'],
} satisfies Record<string, string[]>

export type TokenPreset = keyof typeof TOKEN_PRESETS | 'custom'

export const TOKEN_CAPABILITIES = [
  { scope: 'skill:read', label: 'read' },
  { scope: 'skill:submit', label: 'submit' },
  { scope: 'skill:publish-private', label: 'publishPrivate' },
  { scope: 'skill:manage', label: 'manage' },
  { scope: 'namespace:read', label: 'namespaceRead' },
  { scope: 'namespace:manage', label: 'namespaceManage' },
  { scope: 'skill:delete', label: 'delete' },
  { scope: 'token:manage', label: 'tokenManage' },
] as const

export function getTokenPreset(scopes?: readonly string[]): TokenPreset | undefined {
  if (!scopes) return undefined
  for (const preset of Object.keys(TOKEN_PRESETS) as (keyof typeof TOKEN_PRESETS)[]) {
    const expected = TOKEN_PRESETS[preset]
    if (scopes.length === expected.length && expected.every(scope => scopes.includes(scope))) return preset
  }
  return 'custom'
}
