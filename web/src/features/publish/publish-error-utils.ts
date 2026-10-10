const PRECHECK_CONFIRM_MARKERS = [
  'Pre-publish warnings require confirmation before publishing',
  '预发布发现以下风险提醒，确认后仍可继续发布',
]

const PRECHECK_FAILURE_MARKERS = [
  'error.skill.publish.precheck.failed',
  'Pre-publish validation failed',
  '预发布校验失败',
  'looks like a secret or token',
]

const VERSION_EXISTS_MARKERS = [
  'error.skill.version.exists',
  'Version already exists',
  '版本已存在',
]

const FRONTMATTER_FAILURE_MARKERS = [
  'Invalid SKILL.md frontmatter',
  '技能包校验失败：Invalid SKILL.md frontmatter',
]

function includesAnyMarker(message: string | undefined, markers: string[]): boolean {
  if (!message) {
    return false
  }

  return markers.some((marker) => message.includes(marker))
}

export function isVersionExistsMessage(message?: string): boolean {
  return includesAnyMarker(message, VERSION_EXISTS_MARKERS)
}

export function isPrecheckFailureMessage(message?: string): boolean {
  return includesAnyMarker(message, PRECHECK_FAILURE_MARKERS)
}

export function isPrecheckConfirmationMessage(message?: string): boolean {
  return includesAnyMarker(message, PRECHECK_CONFIRM_MARKERS)
}

export function isFrontmatterFailureMessage(message?: string): boolean {
  return includesAnyMarker(message, FRONTMATTER_FAILURE_MARKERS)
}

export function extractPrecheckWarnings(message?: string): string[] {
  if (!message) {
    return []
  }

  const normalized = message.replace(/\r/g, '').trim()
  if (!normalized) {
    return []
  }

  return normalized
    .split('\n')
    .map((line, index) => {
      const trimmed = line.trim()
      if (!trimmed) {
        return null
      }

      if (index === 0 && isPrecheckConfirmationMessage(trimmed)) {
        const firstWarning = trimmed.replace(/^.*?[：:]\s*/, '').trim()
        return firstWarning && !isPrecheckConfirmationMessage(firstWarning) ? firstWarning : null
      }

      return trimmed.replace(/^[-*•]\s*/, '')
    })
    .filter((line): line is string => Boolean(line))
}
