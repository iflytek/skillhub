export interface SkillhubIntent { readonly id: string; readonly event: string }
export function observeSkillhubIntents(options?: {
  root?: Document | Element
  onIntent?: (intent: SkillhubIntent) => void
}): () => void
