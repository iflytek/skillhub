import type { SkillFile } from '@/api/types'

export type PackageRelativeLinkResolution =
  | {
    status: 'ignored'
    href: string
  }
  | {
    status: 'matched'
    href: string
    path: string
    fragment: string | null
    file: SkillFile
  }
  | {
    status: 'missing'
    href: string
    path: string | null
    fragment: string | null
  }

function splitHref(href: string) {
  const hashIndex = href.indexOf('#')
  const beforeHash = hashIndex >= 0 ? href.slice(0, hashIndex) : href
  const fragment = hashIndex >= 0 ? href.slice(hashIndex + 1) : null
  const queryIndex = beforeHash.indexOf('?')
  return {
    path: queryIndex >= 0 ? beforeHash.slice(0, queryIndex) : beforeHash,
    fragment,
  }
}

function decodePath(path: string) {
  try {
    return decodeURIComponent(path)
  } catch {
    return path
  }
}

function directoryOf(filePath?: string | null) {
  if (!filePath) {
    return ''
  }
  const normalized = filePath.replace(/^\/+/, '')
  const lastSlash = normalized.lastIndexOf('/')
  return lastSlash >= 0 ? normalized.slice(0, lastSlash) : ''
}

function normalizePackagePath(baseDirectory: string, relativePath: string) {
  const stack: string[] = []
  const rawParts = [...baseDirectory.split('/'), ...relativePath.split('/')]

  for (const part of rawParts) {
    if (!part || part === '.') {
      continue
    }
    if (part === '..') {
      if (stack.length === 0) {
        return null
      }
      stack.pop()
      continue
    }
    stack.push(part)
  }

  return stack.join('/')
}

function shouldIgnoreLink(href: string, rawPath: string) {
  if (!href.trim()) {
    return true
  }
  if (!rawPath || href.startsWith('#')) {
    return true
  }
  if (rawPath.startsWith('/') || rawPath.startsWith('//')) {
    return true
  }
  return /^[a-z][a-z0-9+.-]*:/i.test(rawPath)
}

export function resolvePackageRelativeLink(
  href: string,
  currentFilePath: string | null | undefined,
  files: SkillFile[] | null | undefined,
): PackageRelativeLinkResolution {
  const { path: rawPath, fragment } = splitHref(href)

  if (shouldIgnoreLink(href, rawPath)) {
    return { status: 'ignored', href }
  }

  const normalizedPath = normalizePackagePath(directoryOf(currentFilePath), decodePath(rawPath))
  if (!normalizedPath) {
    return { status: 'missing', href, path: null, fragment }
  }

  const matchedFile = (files ?? []).find((file) => file.filePath === normalizedPath)
  if (!matchedFile) {
    return { status: 'missing', href, path: normalizedPath, fragment }
  }

  return {
    status: 'matched',
    href,
    path: normalizedPath,
    fragment,
    file: matchedFile,
  }
}
