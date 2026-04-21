import { request, FormData as UndiciFormData } from "undici";

export interface ApiClientOptions {
  baseUrl: string;
  token?: string;
}

interface NativeApiResponse<T> {
  code: number;
  msg: string;
  data: T;
  timestamp: string;
}

export class ApiClient {
  constructor(private options: ApiClientOptions) {}

  /**
   * Unwrap Native API response format:
   * { code: 0, msg: "success", data: T } -> returns T
   * { code: non-zero, msg: "error", data: null } -> throws ApiError
   *
   * Pass-through Compat API format (no code field):
   * { user: {...} } -> returns as-is
   * { results: [...] } -> returns as-is
   */
  private unwrapResponse<T>(data: unknown): T {
    // Check if it's a Native API response (has code field)
    if (typeof data === "object" && data !== null && "code" in data) {
      const native = data as NativeApiResponse<unknown>;
      if (native.code !== 0) {
        throw new ApiError(native.code, native);
      }
      return native.data as T;
    }
    // Otherwise it's a Compat API response, return as-is
    return data as T;
  }

  async get<T>(path: string): Promise<T> {
    const url = new URL(path, this.options.baseUrl);
    const { statusCode, body } = await request(url.toString(), {
      method: "GET",
      headers: this.headers(),
    });
    const data = await body.json();
    if (statusCode >= 400) {
      throw new ApiError(statusCode, data);
    }
    return this.unwrapResponse<T>(data);
  }

  async postForm<T>(path: string, form: UndiciFormData, queryParams?: Record<string, string>): Promise<T> {
    const url = new URL(path, this.options.baseUrl);
    if (queryParams) {
      for (const [k, v] of Object.entries(queryParams)) {
        url.searchParams.set(k, v);
      }
    }
    const { statusCode, body } = await request(url.toString(), {
      method: "POST",
      headers: {
        ...this.headers(),
      },
      body: form,
    });
    const data = await body.json();
    if (statusCode >= 400) {
      throw new ApiError(statusCode, data);
    }
    return this.unwrapResponse<T>(data);
  }

  async post<T>(path: string, opts?: { body?: string; headers?: Record<string, string> }): Promise<T> {
    const url = new URL(path, this.options.baseUrl);
    const { statusCode, body } = await request(url.toString(), {
      method: "POST",
      headers: { ...this.headers(), ...opts?.headers },
      body: opts?.body,
    });
    const data = await body.json();
    if (statusCode >= 400) {
      throw new ApiError(statusCode, data);
    }
    return this.unwrapResponse<T>(data);
  }

  async put<T>(path: string, opts?: { body?: string; headers?: Record<string, string> }): Promise<T> {
    const url = new URL(path, this.options.baseUrl);
    const { statusCode, body } = await request(url.toString(), {
      method: "PUT",
      headers: { ...this.headers(), ...opts?.headers },
      body: opts?.body,
    });
    const data = await body.json();
    if (statusCode >= 400) {
      throw new ApiError(statusCode, data);
    }
    return this.unwrapResponse<T>(data);
  }

  async delete<T>(path: string): Promise<T> {
    const url = new URL(path, this.options.baseUrl);
    const { statusCode, body } = await request(url.toString(), {
      method: "DELETE",
      headers: this.headers(),
    });
    const data = await body.json();
    if (statusCode >= 400) {
      throw new ApiError(statusCode, data);
    }
    return this.unwrapResponse<T>(data);
  }

  private headers(): Record<string, string> {
    const h: Record<string, string> = {};
    if (this.options.token) {
      h["Authorization"] = `Bearer ${this.options.token}`;
    }
    return h;
  }
}

function extractHumanMessage(body: unknown): string | null {
  if (typeof body !== "object" || body === null) return null;

  const b = body as Record<string, unknown>;

  // Native API: { code, msg, data } — "msg" is authoritative
  if (typeof b.msg === "string" && b.msg.length > 0) return b.msg;
  if (typeof b.message === "string" && b.message.length > 0) return b.message;
  if (typeof b.error === "string" && b.error.length > 0) return b.error;

  return null;
}

export class ApiError extends Error {
  constructor(
    public statusCode: number,
    public body: unknown,
  ) {
    const msg = extractHumanMessage(body);
    let detail = msg ?? `HTTP ${statusCode}`;

    if (statusCode === 401 || statusCode === 403) {
      detail += "\nRun `skillhub login` to authenticate.";
    }

    super(detail);
  }
}
