import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";

const LOCK_FILE_VERSION = 1;
const LOCK_DIR = join(homedir(), ".skillhub");
const LOCK_FILE = join(LOCK_DIR, "lock.json");

export interface SkillLockEntry {
  source: string;
  sourceType: "git" | "registry" | "local";
  sourceUrl: string;
  ref?: string;
  namespace: string;
  slug: string;
  version: string;
  fingerprint?: string;
  installedAt: string;
  updatedAt: string;
}

export interface SkillLockFile {
  version: number;
  skills: Record<string, SkillLockEntry>;
  lastSelectedAgents?: string[];
}

function createEmptyLock(): SkillLockFile {
  return {
    version: LOCK_FILE_VERSION,
    skills: {},
  };
}

export function getSkillLockPath(): string {
  return LOCK_FILE;
}

export async function readSkillLock(): Promise<SkillLockFile> {
  if (!existsSync(LOCK_FILE)) {
    return createEmptyLock();
  }
  try {
    const content = readFileSync(LOCK_FILE, "utf-8");
    const lock = JSON.parse(content) as SkillLockFile;
    if (typeof lock.version !== "number" || !lock.skills) {
      return createEmptyLock();
    }
    return lock;
  } catch {
    return createEmptyLock();
  }
}

export async function writeSkillLock(lock: SkillLockFile): Promise<void> {
  if (!existsSync(LOCK_DIR)) {
    mkdirSync(LOCK_DIR, { recursive: true });
  }
  writeFileSync(LOCK_FILE, JSON.stringify(lock, null, 2));
}

export async function addToLock(
  name: string,
  entry: Omit<SkillLockEntry, "installedAt" | "updatedAt">
): Promise<void> {
  const lock = await readSkillLock();
  const now = new Date().toISOString();
  const existing = lock.skills[name];
  lock.skills[name] = {
    ...entry,
    installedAt: existing?.installedAt ?? now,
    updatedAt: now,
  };
  await writeSkillLock(lock);
}

export async function removeFromLock(name: string): Promise<boolean> {
  const lock = await readSkillLock();
  if (!(name in lock.skills)) {
    return false;
  }
  delete lock.skills[name];
  await writeSkillLock(lock);
  return true;
}

export async function getFromLock(name: string): Promise<SkillLockEntry | null> {
  const lock = await readSkillLock();
  return lock.skills[name] ?? null;
}

export async function getAllLockedSkills(): Promise<Record<string, SkillLockEntry>> {
  const lock = await readSkillLock();
  return lock.skills;
}

export async function getLastSelectedAgents(): Promise<string[] | undefined> {
  const lock = await readSkillLock();
  return lock.lastSelectedAgents;
}

export async function saveLastSelectedAgents(agents: string[]): Promise<void> {
  const lock = await readSkillLock();
  lock.lastSelectedAgents = agents;
  await writeSkillLock(lock);
}
