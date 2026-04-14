import { describe, it, expect } from "vitest";
import { parseSkillName } from "../src/core/skill-name.js";

describe("parseSkillName", () => {
  it("should parse namespace/slug format", () => {
    const result = parseSkillName("global/test");
    expect(result.namespace).toBe("global");
    expect(result.slug).toBe("test");
  });

  it("should use default namespace for plain slug", () => {
    const result = parseSkillName("test");
    expect(result.namespace).toBe("global");
    expect(result.slug).toBe("test");
  });

  it("should allow custom default namespace", () => {
    const result = parseSkillName("test", "vision2group");
    expect(result.namespace).toBe("vision2group");
    expect(result.slug).toBe("test");
  });

  it("should handle team/namespace format", () => {
    const result = parseSkillName("vision2group/test-publish");
    expect(result.namespace).toBe("vision2group");
    expect(result.slug).toBe("test-publish");
  });

  it("should handle slug with multiple slashes (use first two parts)", () => {
    const result = parseSkillName("a/b/c");
    expect(result.namespace).toBe("a");
    expect(result.slug).toBe("b/c");
  });
});
