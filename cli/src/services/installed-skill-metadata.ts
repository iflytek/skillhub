import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { pathExists } from '../platform/paths'

export interface InstalledSkillIdentity {
  registry: string
  namespace: string
  slug: string
}

export interface InstalledSkillMetadata extends InstalledSkillIdentity {
  schemaVersion?: number
  version: string
  versionId?: number
  fingerprint?: string
  files?: Record<string, string>
  source?: string
  agent?: string
  installedAt?: string
}

export type InstalledMetadataReadResult =
  | { status: 'missing' }
  | { status: 'invalid'; reason: string }
  | { status: 'valid'; metadata: InstalledSkillMetadata }

export async function readInstalledSkillMetadata(skillDir: string): Promise<InstalledMetadataReadResult> {
  const metadataPath = join(skillDir, '.skillhub', 'metadata.json')
  if (!(await pathExists(metadataPath))) return { status: 'missing' }

  try {
    const value = JSON.parse(await readFile(metadataPath, 'utf-8')) as unknown
    if (!isRecord(value)) return { status: 'invalid', reason: 'metadata root must be an object' }

    for (const field of ['registry', 'namespace', 'slug', 'version'] as const) {
      if (typeof value[field] !== 'string' || value[field].length === 0) {
        return { status: 'invalid', reason: `metadata field "${field}" must be a non-empty string` }
      }
    }
    if (value.source !== undefined && value.source !== 'skillhub') {
      return { status: 'invalid', reason: 'metadata field "source" must be "skillhub"' }
    }
    if (value.schemaVersion !== undefined && value.schemaVersion !== 1) {
      return { status: 'invalid', reason: 'metadata schema version is not supported' }
    }
    if (value.versionId !== undefined &&
        (!Number.isInteger(value.versionId) || (value.versionId as number) <= 0)) {
      return { status: 'invalid', reason: 'metadata field "versionId" must be a positive integer' }
    }
    if (value.fingerprint !== undefined && typeof value.fingerprint !== 'string') {
      return { status: 'invalid', reason: 'metadata field "fingerprint" must be a string' }
    }
    if (value.files !== undefined && !isStringRecord(value.files)) {
      return { status: 'invalid', reason: 'metadata field "files" must map paths to hashes' }
    }

    return { status: 'valid', metadata: value as unknown as InstalledSkillMetadata }
  } catch {
    return { status: 'invalid', reason: 'metadata is not valid JSON' }
  }
}

export function sameInstalledSkillSource(
  left: InstalledSkillIdentity,
  right: InstalledSkillIdentity
): boolean {
  return normalizeRegistry(left.registry) === normalizeRegistry(right.registry) &&
    left.namespace === right.namespace &&
    left.slug === right.slug
}

function normalizeRegistry(registry: string): string {
  return registry.replace(/\/+$/, '')
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isStringRecord(value: unknown): value is Record<string, string> {
  return isRecord(value) && Object.values(value).every(entry => typeof entry === 'string')
}
