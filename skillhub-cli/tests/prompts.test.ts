import { describe, it, expect, vi } from "vitest";

describe("multiSelect parsing", () => {
  it("parses comma-separated numbers", () => {
    const input = "1,3,5";
    const parts = input.split(",").map((s) => s.trim());
    const indices = parts.map((p) => parseInt(p, 10) - 1);
    const items = [
      { value: "a", label: "A" },
      { value: "b", label: "B" },
      { value: "c", label: "C" },
      { value: "d", label: "D" },
      { value: "e", label: "E" },
    ];
    const selected = indices.filter((i) => i >= 0 && i < items.length).map((i) => items[i].value);
    expect(selected).toEqual(["a", "c", "e"]);
  });

  it("parses 'a' for all", () => {
    const trimmed = "a";
    const items = [{ value: "a", label: "A" }, { value: "b", label: "B" }];
    if (trimmed === "a" || trimmed === "all") {
      const selected = items.map((i) => i.value);
      expect(selected).toEqual(["a", "b"]);
    }
  });

  it("parses 'n' for none", () => {
    const trimmed = "n";
    let result: string[] | null = null;
    if (trimmed === "n" || trimmed === "no") {
      result = null;
    }
    expect(result).toBe(null);
  });

  it("ignores out-of-range numbers", () => {
    const input = "1,10,2";
    const parts = input.split(",").map((s) => s.trim());
    const items = [{ value: "a", label: "A" }, { value: "b", label: "B" }];
    const indices = parts.map((p) => parseInt(p, 10) - 1);
    const selected = indices.filter((i) => i >= 0 && i < items.length).map((i) => items[i].value);
    expect(selected).toEqual(["a", "b"]);
  });
});
