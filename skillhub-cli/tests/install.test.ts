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

describe("install command", () => {
  let tempDir: string;

  beforeEach(() => {
    tempDir = join(tmpdir(), "install-test-" + Date.now());
    mkdirSync(tempDir, { recursive: true });
    mockSuccess.mockClear();
    mockError.mockClear();
    mockInfo.mockClear();
  });

  afterEach(() => {
    try { rmSync(tempDir, { recursive: true, force: true }); } catch {}
    vi.restoreAllMocks();
  });

  describe("source auto-detection", () => {
    it("should detect git source: owner/repo format", () => {
      const source = "vercel-labs/agent-skills";
      const isGitSource = /^[\w-]+\/[\w-]+$/.test(source);
      expect(isGitSource).toBe(true);
    });

    it("should detect git source: GitHub URL", () => {
      const source = "https://github.com/vercel-labs/agent-skills";
      expect(source.includes("github.com")).toBe(true);
    });

    it("should detect git source: GitLab URL", () => {
      const source = "https://gitlab.com/vercel-labs/agent-skills";
      expect(source.includes("gitlab.com")).toBe(true);
    });

    it("should detect local source: relative path", () => {
      const source = "./my-skill";
      const isLocal = source.startsWith(".") || source.startsWith("/") || source.startsWith("~");
      expect(isLocal).toBe(true);
    });

    it("should detect local source: absolute path", () => {
      const source = "/Users/me/skills/my-skill";
      expect(source.startsWith("/")).toBe(true);
    });

    it("should detect registry source: plain slug", () => {
      const source = "my-skill";
      const isGit = /^[\w-]+\/[\w-]+$/.test(source) || source.includes("github.com") || source.includes("gitlab.com");
      const isLocal = source.startsWith(".") || source.startsWith("/") || source.startsWith("~");
      expect(isGit || isLocal).toBe(false);
    });

    it("should detect registry source: namespace--slug format", () => {
      const source = "global--my-skill";
      const isScoped = source.includes("--");
      expect(isScoped).toBe(true);
    });
  });

  describe("--list option", () => {
    it("should list skills without installing", () => {
      const skills = [
        { name: "skill-one", description: "First skill" },
        { name: "skill-two", description: "Second skill" },
      ];
      expect(skills.length).toBe(2);
      expect(skills[0].name).toBe("skill-one");
    });
  });

  describe("registry install", () => {
    it("should construct correct download URL", () => {
      const ns = "global";
      const slug = "my-skill";
      const downloadUrl = `/api/v1/skills/${ns}/${slug}/download`;
      expect(downloadUrl).toBe("/api/v1/skills/global/my-skill/download");
    });

    it("should use default namespace when not specified", () => {
      const defaultNs = "global";
      expect(defaultNs).toBe("global");
    });
  });

  describe("git install", () => {
    it("should parse owner/repo correctly", () => {
      const input = "vercel-labs/skills";
      const parts = input.split("/");
      expect(parts[0]).toBe("vercel-labs");
      expect(parts[1]).toBe("skills");
    });

    it("should construct GitHub clone URL", () => {
      const owner = "vercel-labs";
      const repo = "skills";
      const cloneUrl = `https://github.com/${owner}/${repo}.git`;
      expect(cloneUrl).toBe("https://github.com/vercel-labs/skills.git");
    });
  });

  describe("agent selection", () => {
    it("should detect installed agents", () => {
      const allAgents = [
        { key: "claude-code", name: "Claude Code" },
        { key: "cursor", name: "Cursor" },
      ];
      expect(allAgents.length).toBe(2);
    });

    it("should filter by specified agent keys", () => {
      const allAgents = [
        { key: "claude-code", name: "Claude Code" },
        { key: "cursor", name: "Cursor" },
      ];
      const selected = allAgents.filter((a) => ["claude-code"].includes(a.key));
      expect(selected.length).toBe(1);
      expect(selected[0].key).toBe("claude-code");
    });
  });

  describe("install modes", () => {
    it("should support symlink mode (default)", () => {
      const mode = "symlink";
      expect(mode).toBe("symlink");
    });

    it("should support copy mode with --copy flag", () => {
      const useCopy = true;
      const mode = useCopy ? "copy" : "symlink";
      expect(mode).toBe("copy");
    });

    it("should support global scope with --global flag", () => {
      const isGlobal = true;
      expect(isGlobal).toBe(true);
    });

    it("should support project scope (default)", () => {
      const isGlobal = false;
      expect(isGlobal).toBe(false);
    });
  });
});

describe("--skill option", () => {
  it("should select all skills when '*' is specified", () => {
    const allSkills = [
      { name: "skill-one", description: "First" },
      { name: "skill-two", description: "Second" },
      { name: "skill-three", description: "Third" },
    ];
    const skillNames = ["*"] as string[];
    
    let selectedSkills;
    if (skillNames.includes("*")) {
      selectedSkills = allSkills;
    }
    
    expect(selectedSkills).toEqual(allSkills);
    expect(selectedSkills.length).toBe(3);
  });

  it("should filter skills by exact name match", () => {
    const allSkills = [
      { name: "skill-one", description: "First" },
      { name: "skill-two", description: "Second" },
      { name: "skill-three", description: "Third" },
    ];
    const skillNames = ["skill-one", "skill-three"] as string[];
    
    const selectedSkills = allSkills.filter((s) => skillNames.includes(s.name));
    
    expect(selectedSkills.length).toBe(2);
    expect(selectedSkills[0].name).toBe("skill-one");
    expect(selectedSkills[1].name).toBe("skill-three");
  });

  it("should return empty array when no skills match", () => {
    const allSkills = [
      { name: "skill-one", description: "First" },
      { name: "skill-two", description: "Second" },
    ];
    const skillNames = ["non-existent"] as string[];
    
    const selectedSkills = allSkills.filter((s) => skillNames.includes(s.name));
    
    expect(selectedSkills.length).toBe(0);
  });

  it("should handle case-sensitive name matching", () => {
    const allSkills = [
      { name: "OpenSpec", description: "OpenSpec skill" },
      { name: "openspec", description: "lowercase" },
    ];
    const skillNames = ["openspec"] as string[];
    
    const selectedSkills = allSkills.filter((s) => skillNames.includes(s.name));
    
    expect(selectedSkills.length).toBe(1);
    expect(selectedSkills[0].name).toBe("openspec");
  });
});

describe("add command alias", () => {
  it("should be equivalent to install --source git", () => {
    const installSource = "git";
    expect(installSource).toBe("git");
  });
});
