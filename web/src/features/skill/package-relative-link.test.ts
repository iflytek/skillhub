import { describe, expect, it } from 'vitest'
import type { SkillFile } from '@/api/types'
import { resolvePackageRelativeLink } from './package-relative-link'

function file(filePath: string): SkillFile {
  return {
    id: filePath.length,
    filePath,
    fileSize: 128,
    contentType: 'text/markdown',
    sha256: `sha-${filePath}`,
  }
}

const packageFiles = [
  file('README.md'),
  file('docs/SKILL.md'),
  file('docs/usage.md'),
  file('shared.md'),
  file('space name.md'),
  file('使用.md'),
]

describe('resolvePackageRelativeLink', () => {
  it('matches same-directory and explicit current-directory links from the package root', () => {
    expect(resolvePackageRelativeLink('docs/usage.md', 'README.md', packageFiles)).toMatchObject({
      status: 'matched',
      path: 'docs/usage.md',
    })
    expect(resolvePackageRelativeLink('./docs/usage.md', 'README.md', packageFiles)).toMatchObject({
      status: 'matched',
      path: 'docs/usage.md',
    })
  })

  it('normalizes parent-directory links against the current documentation file', () => {
    expect(resolvePackageRelativeLink('../shared.md', 'docs/SKILL.md', packageFiles)).toMatchObject({
      status: 'matched',
      path: 'shared.md',
    })
  })

  it('keeps fragment information while matching the file path', () => {
    expect(resolvePackageRelativeLink('docs/usage.md#intro', 'README.md', packageFiles)).toMatchObject({
      status: 'matched',
      path: 'docs/usage.md',
      fragment: 'intro',
    })
  })

  it('decodes encoded file paths before matching package files', () => {
    expect(resolvePackageRelativeLink('space%20name.md', 'README.md', packageFiles)).toMatchObject({
      status: 'matched',
      path: 'space name.md',
    })
    expect(resolvePackageRelativeLink('%E4%BD%BF%E7%94%A8.md', 'README.md', packageFiles)).toMatchObject({
      status: 'matched',
      path: '使用.md',
    })
  })

  it('ignores links that should keep native browser behavior', () => {
    for (const href of ['https://example.com', 'mailto:team@example.com', '#intro', '/absolute/path.md', '']) {
      expect(resolvePackageRelativeLink(href, 'README.md', packageFiles)).toMatchObject({
        status: 'ignored',
      })
    }
  })

  it('returns missing for relative links that do not resolve to a package file', () => {
    expect(resolvePackageRelativeLink('docs/missing.md', 'README.md', packageFiles)).toMatchObject({
      status: 'missing',
      path: 'docs/missing.md',
    })
    expect(resolvePackageRelativeLink('../../outside.md', 'docs/SKILL.md', packageFiles)).toMatchObject({
      status: 'missing',
      path: null,
    })
  })
})
