export interface ParsedSkillName {
  namespace: string
  slug: string
}

export function parseSkillName(skillName: string, defaultNamespace = 'global'): ParsedSkillName {
  const separatorIndex = skillName.indexOf('--')

  if (separatorIndex <= 0) {
    return {
      namespace: defaultNamespace,
      slug: separatorIndex === 0 ? skillName.slice(2) : skillName
    }
  }

  if (separatorIndex === skillName.length - 2) {
    return {
      namespace: defaultNamespace,
      slug: skillName.slice(0, -2)
    }
  }

  return {
    namespace: skillName.slice(0, separatorIndex),
    slug: skillName.slice(separatorIndex + 2)
  }
}
