import { describe, it, expect, beforeEach, afterEach } from "vitest";
import { installSkill } from "../src/core/installer.js";
import { mkdirSync, writeFileSync, rmSync } from "node:fs";
import { join } from "node:path";
import { tmpdir } from "node:os";

describe("installSkill", () => {
  let tempDir: string;
  let skillDir: string;
  let targetDir: string;

  beforeEach(() => {
    tempDir = join(tmpdir(), "installer-test-" + Date.now());
    skillDir = join(tempDir, "source-skill");
    targetDir = tempDir;
    mkdirSync(skillDir, { recursive: true });
    writeFileSync(join(skillDir, "SKILL.md"), "# Test Skill\n");
  });

  afterEach(() => {
    try { rmSync(tempDir, { recursive: true, force: true }); } catch {}
  });

  it("should return correct mode when mode is 'symlink'", () => {
    const result = installSkill(skillDir, "test-skill", "claude-code", targetDir, "symlink", false);
    expect(result.mode).toBe("symlink");
    expect(result.success).toBe(true);
  });

  it("should return correct mode when mode is 'copy'", () => {
    const result = installSkill(skillDir, "test-skill", "claude-code", targetDir, "copy", false);
    expect(result.mode).toBe("copy");
    expect(result.success).toBe(true);
  });
});
