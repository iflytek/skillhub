import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const LOCK_FILE = "skillhub-lock.json";

export interface LockEntry {
  source: string;
  version: string;
  installedAt: string;
}

export interface SkillLock {
  skills: Record<string, LockEntry>;
}

export function loadLock(dir: string): SkillLock {
  const lockPath = join(dir, LOCK_FILE);
  if (!existsSync(lockPath)) return { skills: {} };
  return JSON.parse(readFileSync(lockPath, "utf-8"));
}

export function saveLock(dir: string, lock: SkillLock): void {
  if (!existsSync(dir)) {
    mkdirSync(dir, { recursive: true });
  }
  writeFileSync(join(dir, LOCK_FILE), JSON.stringify(lock, null, 2));
}

export function addSkill(lock: SkillLock, name: string, entry: LockEntry): void {
  lock.skills[name] = entry;
}

export function removeSkill(lock: SkillLock, name: string): boolean {
  if (lock.skills[name]) {
    delete lock.skills[name];
    return true;
  }
  return false;
}
