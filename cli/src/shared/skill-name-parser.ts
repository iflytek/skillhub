import { EXIT } from './constants'
import { CliError } from './errors'

export interface ParsedSkillName {
  namespace: string
  slug: string
}

interface ParsedCoordinate {
  namespace?: string
  slug: string
}

function invalidCoordinate(skillName: string): CliError {
  return new CliError(`invalid skill coordinate "${skillName}"`, EXIT.usage)
}

function parseSeparatedCoordinate(
  skillName: string,
  separatorIndex: number,
  separatorLength: number,
  namespaceStart = 0
): ParsedCoordinate {
  const namespace = skillName.slice(namespaceStart, separatorIndex)
  const slug = skillName.slice(separatorIndex + separatorLength)

  if (!namespace || !slug || slug.includes('/')) {
    throw invalidCoordinate(skillName)
  }

  return { namespace, slug }
}

function parseCoordinate(skillName: string): ParsedCoordinate {
  if (!skillName) {
    throw invalidCoordinate(skillName)
  }

  const slashIndex = skillName.indexOf('/')

  if (skillName.startsWith('@')) {
    if (slashIndex < 0) {
      throw invalidCoordinate(skillName)
    }
    return parseSeparatedCoordinate(skillName, slashIndex, 1, 1)
  }

  const doubleDashIndex = skillName.indexOf('--')
  if (slashIndex >= 0 && (doubleDashIndex < 0 || slashIndex < doubleDashIndex)) {
    return parseSeparatedCoordinate(skillName, slashIndex, 1)
  }
  if (doubleDashIndex >= 0) {
    return parseSeparatedCoordinate(skillName, doubleDashIndex, 2)
  }

  return { slug: skillName }
}

export function parseSkillName(skillName: string, defaultNamespace = 'global'): ParsedSkillName {
  const parsed = parseCoordinate(skillName)
  return {
    namespace: parsed.namespace ?? defaultNamespace,
    slug: parsed.slug
  }
}

/** Return whether a skill coordinate explicitly includes a namespace. */
export function hasExplicitNamespace(skillName: string): boolean {
  return parseCoordinate(skillName).namespace !== undefined
}

/** Resolve a skill coordinate and an optional command-line namespace into one registry identity. */
export function resolveSkillName(skillName: string, explicitNamespace?: string): ParsedSkillName {
  const parsed = parseCoordinate(skillName)

  if (
    parsed.namespace !== undefined &&
    explicitNamespace !== undefined &&
    parsed.namespace !== explicitNamespace
  ) {
    throw new CliError(
      `skill coordinate namespace "${parsed.namespace}" conflicts with --namespace "${explicitNamespace}"`,
      EXIT.usage
    )
  }

  return {
    namespace: parsed.namespace ?? explicitNamespace ?? 'global',
    slug: parsed.slug
  }
}
