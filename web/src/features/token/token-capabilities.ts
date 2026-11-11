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
  { scope: 'skill:delete', label: 'delete' },
  { scope: 'token:manage', label: 'tokenManage' },
] as const
