import { describe, it, expect, vi, beforeEach } from "vitest";

vi.mock("undici", () => ({
  request: vi.fn(),
  FormData: class FormData {
    private _data = new Map<string, string>();
    set(k: string, v: string) { this._data.set(k, v); }
    append(k: string, v: unknown) { this._data.set(k, String(v)); }
  },
}));

import { ApiClient, ApiError } from "../src/core/api-client.js";
import { request } from "undici";

const mockRequest = request as ReturnType<typeof vi.fn>;

function mockResponse(statusCode: number, body: unknown) {
  mockRequest.mockResolvedValueOnce({
    statusCode,
    body: { json: async () => body },
  });
}

describe("ApiClient", () => {
  let client: ApiClient;

  beforeEach(() => {
    vi.clearAllMocks();
    client = new ApiClient({ baseUrl: "http://localhost:8080" });
  });

  describe("ApiResponse unwrapping", () => {
    it("unwraps Native API success response", async () => {
      mockResponse(200, {
        code: 0,
        msg: "success",
        data: { id: 1, name: "test" },
        timestamp: "2026-01-01T00:00:00Z",
      });

      const result = await client.get<{ id: number; name: string }>("/api/v1/test");

      expect(result).toEqual({ id: 1, name: "test" });
      expect(mockRequest).toHaveBeenCalledTimes(1);
    });

    it("throws ApiError on Native API error response", async () => {
      mockResponse(200, {
        code: 403,
        msg: "Forbidden",
        data: null,
        timestamp: "2026-01-01T00:00:00Z",
      });

      await expect(client.get("/api/v1/test")).rejects.toThrow(ApiError);
    });

    it("returns raw response for Compat layer (no code/data)", async () => {
      mockResponse(200, {
        user: { handle: "test-user", displayName: "Test", image: null },
      });

      const result = await client.get<{ user: { handle: string } }>("/api/v1/whoami");

      expect(result).toEqual({
        user: { handle: "test-user", displayName: "Test", image: null },
      });
    });

    it("returns raw response for Compat search results", async () => {
      mockResponse(200, {
        results: [
          { slug: "test-skill", displayName: "Test", summary: "A test", version: "1.0.0" },
        ],
      });

      const result = await client.get<{ results: Array<{ slug: string }> }>("/api/v1/search");

      expect(result.results).toHaveLength(1);
      expect(result.results[0].slug).toBe("test-skill");
    });

    it("returns raw response for Compat publish result", async () => {
      mockResponse(200, { ok: true, skillId: "1", versionId: "1" });

      const result = await client.postForm<{ ok: boolean; skillId: string }>("/api/v1/skills", {} as any);

      expect(result.ok).toBe(true);
      expect(result.skillId).toBe("1");
    });
  });

  describe("HTTP error handling", () => {
    it("throws ApiError on HTTP 404", async () => {
      mockResponse(404, { code: 404, msg: "Not found", data: null });

      await expect(client.get("/api/v1/nonexistent")).rejects.toThrow(ApiError);
    });

    it("throws ApiError on HTTP 500", async () => {
      mockResponse(500, { code: 500, msg: "Internal error", data: null });

      await expect(client.get("/api/v1/test")).rejects.toThrow(ApiError);
    });
  });

  describe("Authorization header", () => {
    it("includes Bearer token when provided", async () => {
      mockResponse(200, { code: 0, data: {}, msg: "ok" });

      const clientWithToken = new ApiClient({
        baseUrl: "http://localhost:8080",
        token: "sk_test123",
      });

      await clientWithToken.get("/api/v1/test");

      expect(mockRequest).toHaveBeenCalledWith(
        "http://localhost:8080/api/v1/test",
        expect.objectContaining({
          headers: expect.objectContaining({
            Authorization: "Bearer sk_test123",
          }),
        })
      );
    });

    it("omits Authorization header when no token", async () => {
      mockResponse(200, { results: [] });

      await client.get("/api/v1/search");

      const callArgs = mockRequest.mock.calls[0][1];
      expect(callArgs.headers).not.toHaveProperty("Authorization");
    });
  });

  describe("POST method", () => {
    it("unwraps Native API POST response", async () => {
      mockResponse(200, {
        code: 0,
        data: { id: 1 },
        msg: "created",
      });

      const result = await client.post<{ id: number }>("/api/v1/test", { body: "{}" });

      expect(result).toEqual({ id: 1 });
    });

    it("returns raw Compat POST response", async () => {
      mockResponse(200, { ok: true, skillId: "2" });

      const result = await client.post<{ ok: boolean }>("/api/v1/skills", { body: "{}" });

      expect(result.ok).toBe(true);
    });
  });

  describe("PUT method", () => {
    it("unwraps Native API PUT response", async () => {
      mockResponse(200, { code: 0, data: { updated: true }, msg: "ok" });

      const result = await client.put<{ updated: boolean }>("/api/v1/test", { body: "{}" });

      expect(result.updated).toBe(true);
    });
  });

  describe("DELETE method", () => {
    it("unwraps Native API DELETE response", async () => {
      mockResponse(200, { code: 0, data: { deleted: true }, msg: "ok" });

      const result = await client.delete<{ deleted: boolean }>("/api/v1/test");

      expect(result.deleted).toBe(true);
    });
  });
});
