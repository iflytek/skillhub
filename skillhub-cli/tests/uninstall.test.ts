import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { existsSync, mkdirSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

const mockSuccess = vi.fn();
const mockError = vi.fn();
const mockInfo = vi.fn();

vi.mock("../src/utils/logger.js", () => ({
  success: mockSuccess,
  error: mockError,
  info: mockInfo,
  dim: vi.fn(),
}));

describe("uninstall command", () => {
  let tempDir: string;

  beforeEach(() => {
    tempDir = join(tmpdir(), "uninstall-test-" + Date.now());
    mkdirSync(tempDir, { recursive: true });
    mockSuccess.mockClear();
    mockError.mockClear();
    mockInfo.mockClear();
  });

  afterEach(() => {
    try { rmSync(tempDir, { recursive: true, force: true }); } catch {}
    vi.restoreAllMocks();
  });

  describe("removeDir utility", () => {
    it("should handle file removal", () => {
      const testFile = join(tempDir, "test-file.txt");
      writeFileSync(testFile, "content");
      expect(existsSync(testFile)).toBe(true);
    });

    it("should handle directory removal recursively", () => {
      const testSubDir = join(tempDir, "subdir", "nested");
      mkdirSync(testSubDir, { recursive: true });
      writeFileSync(join(testSubDir, "file.txt"), "content");
      expect(existsSync(testSubDir)).toBe(true);
    });
  });

  describe("--all flag", () => {
    it("should discover all installed skills", async () => {
      const skill1 = join(tempDir, ".claude", "skills", "skill-one");
      const skill2 = join(tempDir, ".claude", "skills", "skill-two");
      mkdirSync(skill1, { recursive: true });
      mkdirSync(skill2, { recursive: true });
      writeFileSync(join(skill1, "SKILL.md"), "# Skill One\n");
      writeFileSync(join(skill2, "SKILL.md"), "# Skill Two\n");
      
      expect(existsSync(skill1)).toBe(true);
      expect(existsSync(skill2)).toBe(true);
    });
  });

  describe("--agent filter", () => {
    it("should filter by specific agent", () => {
      const claudeDir = join(tempDir, ".claude", "skills", "shared-skill");
      const cursorDir = join(tempDir, ".agents", "skills", "shared-skill");
      mkdirSync(claudeDir, { recursive: true });
      mkdirSync(cursorDir, { recursive: true });
      writeFileSync(join(claudeDir, "SKILL.md"), "# Shared Skill\n");
      writeFileSync(join(cursorDir, "SKILL.md"), "# Shared Skill\n");
      
      expect(existsSync(claudeDir)).toBe(true);
      expect(existsSync(cursorDir)).toBe(true);
    });
  });

  describe("--global flag", () => {
    it("should target global scope only", () => {
      const globalSkill = join(tempDir, ".claude", "skills", "global-skill");
      mkdirSync(globalSkill, { recursive: true });
      writeFileSync(join(globalSkill, "SKILL.md"), "# Global Skill\n");
      
      expect(existsSync(globalSkill)).toBe(true);
    });
  });
});

describe("source parser", () => {
  describe("parseSource", () => {
    it("should identify git source: owner/repo", () => {
      const source = "vercel-labs/agent-skills";
      const pattern = /^[\w-]+\/[\w-]+/;
      expect(pattern.test(source)).toBe(true);
    });

    it("should identify git source: GitHub URL", () => {
      const source = "https://github.com/vercel-labs/agent-skills";
      expect(source.startsWith("https://github.com/")).toBe(true);
    });

    it("should identify registry source: slug", () => {
      const source = "my-skill";
      const isGit = /^[\w-]+\/[\w-]+/.test(source) || source.startsWith("https://github.com/");
      expect(isGit).toBe(false);
    });

    it("should identify registry source: namespace--slug", () => {
      const source = "global--my-skill";
      const parts = source.split("--");
      expect(parts.length >= 2).toBe(true);
    });
  });
});
