import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { existsSync, mkdirSync, writeFileSync, rmSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

const LOCK_FILE_VERSION = 1;

interface SkillLockEntry {
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

interface SkillLockFile {
  version: number;
  skills: Record<string, SkillLockEntry>;
  lastSelectedAgents?: string[];
}

function getSkillLockPath(dir: string): string {
  return join(dir, "lock.json");
}

async function readSkillLock(dir: string): Promise<SkillLockFile> {
  const lockPath = getSkillLockPath(dir);
  if (!existsSync(lockPath)) {
    return { version: LOCK_FILE_VERSION, skills: {} };
  }
  const content = readFileSync(lockPath, "utf-8");
  return JSON.parse(content);
}

async function writeSkillLock(dir: string, lock: SkillLockFile): Promise<void> {
  const lockPath = getSkillLockPath(dir);
  mkdirSync(dir, { recursive: true });
  writeFileSync(lockPath, JSON.stringify(lock, null, 2));
}

async function addToLock(dir: string, name: string, entry: SkillLockEntry): Promise<void> {
  const lock = await readSkillLock(dir);
  const now = new Date().toISOString();
  const existing = lock.skills[name];
  lock.skills[name] = {
    ...entry,
    installedAt: existing?.installedAt ?? entry.installedAt ?? now,
    updatedAt: now,
  };
  await writeSkillLock(dir, lock);
}

async function removeFromLock(dir: string, name: string): Promise<boolean> {
  const lock = await readSkillLock(dir);
  if (!(name in lock.skills)) return false;
  delete lock.skills[name];
  await writeSkillLock(dir, lock);
  return true;
}

async function getFromLock(dir: string, name: string): Promise<SkillLockEntry | null> {
  const lock = await readSkillLock(dir);
  return lock.skills[name] ?? null;
}

describe("skill-lock", () => {
  let tempDir: string;

  beforeEach(() => {
    tempDir = join(tmpdir(), "skill-lock-test-" + Date.now());
    mkdirSync(tempDir, { recursive: true });
  });

  afterEach(() => {
    try { rmSync(tempDir, { recursive: true, force: true }); } catch {}
  });

  describe("read/write", () => {
    it("should read empty lock file", async () => {
      const lock = await readSkillLock(tempDir);
      expect(lock.version).toBe(LOCK_FILE_VERSION);
      expect(lock.skills).toEqual({});
    });

    it("should write and read lock file", async () => {
      const entry: SkillLockEntry = {
        source: "owner/repo",
        sourceType: "git",
        sourceUrl: "https://github.com/owner/repo",
        namespace: "global",
        slug: "my-skill",
        version: "1.0.0",
        installedAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      };
      await addToLock(tempDir, "my-skill", entry);
      
      const lock = await readSkillLock(tempDir);
      expect(Object.keys(lock.skills)).toContain("my-skill");
      expect(lock.skills["my-skill"].source).toBe("owner/repo");
    });
  });

  describe("addToLock", () => {
    it("should add new entry", async () => {
      const entry: SkillLockEntry = {
        source: "owner/repo",
        sourceType: "git",
        sourceUrl: "https://github.com/owner/repo",
        namespace: "global",
        slug: "new-skill",
        version: "1.0.0",
        installedAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      };
      await addToLock(tempDir, "new-skill", entry);
      
      const result = await getFromLock(tempDir, "new-skill");
      expect(result).not.toBeNull();
      expect(result!.slug).toBe("new-skill");
    });

    it("should preserve installedAt on update", async () => {
      const entry1: SkillLockEntry = {
        source: "owner/repo",
        sourceType: "git",
        sourceUrl: "https://github.com/owner/repo",
        namespace: "global",
        slug: "skill",
        version: "1.0.0",
        installedAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      };
      await addToLock(tempDir, "skill", entry1);
      
      const savedFirst = await getFromLock(tempDir, "skill");
      expect(savedFirst!.installedAt).toBe("2024-01-01T00:00:00Z");
      
      const entry2: SkillLockEntry = {
        ...entry1,
        version: "1.1.0",
      };
      await addToLock(tempDir, "skill", entry2);
      
      const result = await getFromLock(tempDir, "skill");
      expect(result!.installedAt).toBe("2024-01-01T00:00:00Z");
      expect(result!.version).toBe("1.1.0");
      expect(result!.updatedAt).not.toBe("2024-01-01T00:00:00Z");
    });
  });

  describe("removeFromLock", () => {
    it("should remove existing entry", async () => {
      const entry: SkillLockEntry = {
        source: "owner/repo",
        sourceType: "git",
        sourceUrl: "https://github.com/owner/repo",
        namespace: "global",
        slug: "to-remove",
        version: "1.0.0",
        installedAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      };
      await addToLock(tempDir, "to-remove", entry);
      
      const removed = await removeFromLock(tempDir, "to-remove");
      expect(removed).toBe(true);
      
      const result = await getFromLock(tempDir, "to-remove");
      expect(result).toBeNull();
    });

    it("should return false for non-existent entry", async () => {
      const removed = await removeFromLock(tempDir, "non-existent");
      expect(removed).toBe(false);
    });
  });

  describe("getFromLock", () => {
    it("should return null for non-existent key", async () => {
      const result = await getFromLock(tempDir, "non-existent");
      expect(result).toBeNull();
    });

    it("should return entry for existing key", async () => {
      const entry: SkillLockEntry = {
        source: "global/my-skill",
        sourceType: "registry",
        sourceUrl: "https://registry.example.com/global/my-skill",
        namespace: "global",
        slug: "my-skill",
        version: "2.0.0",
        installedAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      };
      await addToLock(tempDir, "my-skill", entry);
      
      const result = await getFromLock(tempDir, "my-skill");
      expect(result).not.toBeNull();
      expect(result!.version).toBe("2.0.0");
      expect(result!.sourceType).toBe("registry");
    });
  });

  describe("lastSelectedAgents", () => {
    it("should persist lastSelectedAgents", async () => {
      const lock: SkillLockFile = {
        version: LOCK_FILE_VERSION,
        skills: {},
        lastSelectedAgents: ["claude-code", "cursor"],
      };
      await writeSkillLock(tempDir, lock);
      
      const read = await readSkillLock(tempDir);
      expect(read.lastSelectedAgents).toEqual(["claude-code", "cursor"]);
    });
  });
});
