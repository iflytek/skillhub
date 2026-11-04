let savedPosition: { key: string; top: number } | undefined

export function rememberMySkillsScroll(key: string, top: number) {
  savedPosition = { key, top }
}

export function readMySkillsScroll(key: string): number | undefined {
  return savedPosition?.key === key ? savedPosition.top : undefined
}

export function takeMySkillsScroll(key: string): number | undefined {
  if (savedPosition?.key !== key) return undefined
  const top = savedPosition.top
  savedPosition = undefined
  return top
}
