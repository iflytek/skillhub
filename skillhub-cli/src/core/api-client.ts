import { request, FormData as UndiciFormData } from "undici";

export interface ApiClientOptions {
  baseUrl: string;
  token?: string;
}

export class ApiClient {
  constructor(private options: ApiClientOptions) {}

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
    return data as T;
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
    return data as T;
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
    return data as T;
  }

  async put<T>(path: string): Promise<T> {
    const url = new URL(path, this.options.baseUrl);
    const { statusCode, body } = await request(url.toString(), {
      method: "PUT",
      headers: this.headers(),
    });
    const data = await body.json();
    if (statusCode >= 400) {
      throw new ApiError(statusCode, data);
    }
    return data as T;
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
    return data as T;
  }

  private headers(): Record<string, string> {
    const h: Record<string, string> = {};
    if (this.options.token) {
      h["Authorization"] = `Bearer ${this.options.token}`;
    }
    return h;
  }
}

export class ApiError extends Error {
  constructor(
    public statusCode: number,
    public body: unknown,
  ) {
    super(`API error ${statusCode}: ${JSON.stringify(body)}`);
  }
}
