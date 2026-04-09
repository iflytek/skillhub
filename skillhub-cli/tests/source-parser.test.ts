import { describe, it, expect, vi } from "vitest";

// local fs mock
vi.mock("node:fs", () => ({
  existsSync: vi.fn(),
}));

it("parses local path", async () => {
  vi.resetModules();
  vi.doMock("node:fs", () => ({ existsSync: vi.fn().mockReturnValue(true) }));
  const mod = await import("../src/core/source-parser");
  const res = mod.parseSource("/abs/path");
  expect(res.type).toBe("local");
  expect(res.localPath).toBe("/abs/path");
});

it("parses github url from github.com", async () => {
  vi.resetModules();
  const mod = await import("../src/core/source-parser");
  const res = mod.parseSource("https://github.com/owner/repo.git");
  expect(res.type).toBe("github");
  expect(res.owner).toBe("owner");
  expect(res.repo).toBe("repo");
  expect(res.cloneUrl).toBe("https://github.com/owner/repo.git");
});

it("parses shorthand owner/repo", async () => {
  vi.resetModules();
  const mod = await import("../src/core/source-parser");
  const res = mod.parseSource("alice/awesome");
  expect(res.type).toBe("github");
  expect(res.owner).toBe("alice");
  expect(res.repo).toBe("awesome");
});

it("throws on invalid source format", async () => {
  vi.resetModules();
  const mod = await import("../src/core/source-parser");
  expect(() => mod.parseSource("invalid")).toThrow();
});

it("getCloneUrl uses cloneUrl when provided", async () => {
  vi.resetModules();
  const mod = await import("../src/core/source-parser");
  const url = mod.getCloneUrl({ type: "github", owner: "a", repo: "b", cloneUrl: "https://example.com/a/b.git" } as any);
  expect(url).toBe("https://example.com/a/b.git");
});

it("getCloneUrl builds default for github without cloneUrl", async () => {
  vi.resetModules();
  const mod = await import("../src/core/source-parser");
  const url = mod.getCloneUrl({ type: "github", owner: "x", repo: "y" } as any);
  expect(url).toBe("https://github.com/x/y.git");
});

it("parses @skill syntax: owner/repo@skillname", async () => {
  vi.resetModules();
  const mod = await import("../src/core/source-parser");
  const res = mod.parseSource("vercel-labs/skills@openspec");
  expect(res.type).toBe("github");
  expect(res.owner).toBe("vercel-labs");
  expect(res.repo).toBe("skills");
  expect(res.skillFilter).toBe("openspec");
});

it("parses @skill syntax with branch: owner/repo@skillname", async () => {
  vi.resetModules();
  const mod = await import("../src/core/source-parser");
  const res = mod.parseSource("owner/repo@something");
  expect(res.type).toBe("github");
  expect(res.owner).toBe("owner");
  expect(res.repo).toBe("repo");
  expect(res.skillFilter).toBe("something");
});

it("does not confuse @ in path with @skill syntax", async () => {
  vi.resetModules();
  const mod = await import("../src/core/source-parser");
  const res = mod.parseSource("https://github.com/owner/repo");
  expect(res.type).toBe("github");
  expect(res.owner).toBe("owner");
  expect(res.repo).toBe("repo");
  expect(res.skillFilter).toBeUndefined();
});
