interface GitHubUser {
  login: string;
}

interface GitHubLabelRef {
  name?: string;
}

export interface GitHubIssue {
  number: number;
  title: string;
  body: string | null;
  state: string;
  labels: GitHubLabelRef[];
  comments: number;
  created_at: string;
  updated_at: string;
  user: GitHubUser;
  html_url: string;
  pull_request?: Record<string, unknown>;
}

export interface GitHubIssueComment {
  id: number;
  body: string;
  user: GitHubUser;
  created_at: string;
  updated_at: string;
  html_url: string;
}

export interface GitHubLabelDefinition {
  name: string;
  color: string;
  description: string;
}

function buildApiUrl(path: string) {
  return `https://api.github.com${path}`;
}

export class GitHubClient {
  constructor(
    private readonly token: string,
    private readonly owner: string,
    private readonly repo: string,
  ) {}

  async getIssue(issueNumber: number): Promise<GitHubIssue> {
    return this.request<GitHubIssue>(
      "GET",
      `/repos/${this.owner}/${this.repo}/issues/${issueNumber}`,
    );
  }

  async listIssueComments(issueNumber: number): Promise<GitHubIssueComment[]> {
    return this.paginate<GitHubIssueComment>(
      `/repos/${this.owner}/${this.repo}/issues/${issueNumber}/comments?per_page=100`,
    );
  }

  async listOpenIssuesByLabel(
    label: string,
    limit = 0,
  ): Promise<GitHubIssue[]> {
    const collected: GitHubIssue[] = [];
    const unlimited = limit === 0;
    let page = 1;

    while (unlimited || collected.length < limit) {
      const pageItems = await this.request<GitHubIssue[]>(
        "GET",
        `/repos/${this.owner}/${this.repo}/issues?state=open&labels=${
          encodeURIComponent(label)
        }&per_page=100&page=${page}`,
      );

      const nonPrIssues = pageItems.filter((item) => !item.pull_request);
      collected.push(...nonPrIssues);

      if (pageItems.length < 100) {
        break;
      }

      page += 1;
    }

    return unlimited ? collected : collected.slice(0, limit);
  }

  async replaceIssueLabels(issueNumber: number, labels: string[]) {
    await this.request(
      "PUT",
      `/repos/${this.owner}/${this.repo}/issues/${issueNumber}/labels`,
      { labels },
    );
  }

  async upsertLabel(definition: GitHubLabelDefinition) {
    const encodedName = encodeURIComponent(definition.name);

    try {
      await this.request(
        "PATCH",
        `/repos/${this.owner}/${this.repo}/labels/${encodedName}`,
        {
          new_name: definition.name,
          color: definition.color,
          description: definition.description,
        },
      );
    } catch (error) {
      if (!(error instanceof GitHubApiError) || error.status !== 404) {
        throw error;
      }

      await this.request("POST", `/repos/${this.owner}/${this.repo}/labels`, {
        name: definition.name,
        color: definition.color,
        description: definition.description,
      });
    }
  }

  async createIssueComment(issueNumber: number, body: string) {
    return this.request<GitHubIssueComment>(
      "POST",
      `/repos/${this.owner}/${this.repo}/issues/${issueNumber}/comments`,
      { body },
    );
  }

  async updateIssueComment(commentId: number, body: string) {
    return this.request<GitHubIssueComment>(
      "PATCH",
      `/repos/${this.owner}/${this.repo}/issues/comments/${commentId}`,
      { body },
    );
  }

  private async paginate<T>(path: string): Promise<T[]> {
    const collected: T[] = [];
    let nextPath: string | null = path;

    while (nextPath) {
      const response = await fetch(buildApiUrl(nextPath), {
        headers: this.headers(),
      });

      if (!response.ok) {
        throw await GitHubApiError.fromResponse(response);
      }

      const pageItems = (await response.json()) as T[];
      collected.push(...pageItems);
      nextPath = parseNextLink(response.headers.get("link"));
    }

    return collected;
  }

  private async request<T = void>(
    method: string,
    path: string,
    body?: unknown,
  ): Promise<T> {
    const response = await fetch(buildApiUrl(path), {
      method,
      headers: this.headers(),
      body: body ? JSON.stringify(body) : undefined,
    });

    if (!response.ok) {
      throw await GitHubApiError.fromResponse(response);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return (await response.json()) as T;
  }

  private headers() {
    return {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${this.token}`,
      "Content-Type": "application/json",
      "User-Agent": "skillhub-issue-triage",
      "X-GitHub-Api-Version": "2022-11-28",
    };
  }
}

export class GitHubApiError extends Error {
  constructor(
    readonly status: number,
    readonly responseBody: string,
  ) {
    super(`GitHub API request failed with status ${status}: ${responseBody}`);
  }

  static async fromResponse(response: Response) {
    return new GitHubApiError(response.status, await response.text());
  }
}

function parseNextLink(linkHeader: string | null) {
  if (!linkHeader) {
    return null;
  }

  const nextEntry = linkHeader
    .split(",")
    .map((item) => item.trim())
    .find((item) => item.endsWith('rel="next"'));

  if (!nextEntry) {
    return null;
  }

  const urlMatch = nextEntry.match(/<([^>]+)>/);

  if (!urlMatch) {
    return null;
  }

  const url = new URL(urlMatch[1]);
  return `${url.pathname}${url.search}`;
}
