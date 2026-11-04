export interface SkillhubIntent { readonly id: string; readonly event: string }
export type RuleType = 'click' | 'submit' | 'card'
export const DEFAULT_RULES: Readonly<Record<string, RuleType>>
export function resolveRules(rules: unknown): {
  active: Record<string, RuleType>
  skipped: Array<{ id: string | null; reason: string }>
}
export function observeSkillhubIntents(options?: {
  root?: Document | Element
  rules?: unknown
  onIntent?: (intent: SkillhubIntent) => unknown
}): () => void
