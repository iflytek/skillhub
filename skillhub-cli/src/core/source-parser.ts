import { existsSync } from "node:fs";
import { resolve } from "node:path";

const DEFAULT_GITHUB_HOST = "github.com";

function getGitHubHost(): string {
  return process.env.GITHUB_MIRROR || DEFAULT_GITHUB_HOST;
}

function isGitHubHost(hostname: string): boolean {
  const ghHost = getGitHubHost();
  return hostname === ghHost || hostname.endsWith(`.${ghHost}`);
}

export interface ParsedSource {
  type: "local" | "github" | "gitlab" | "url";
  owner?: string;
  repo?: string;
  ref?: string;
  subpath?: string;
  localPath?: string;
  cloneUrl?: string;
  skillFilter?: string;
}

export function parseSource(input: string): ParsedSource {
  if (input.startsWith(".") || input.startsWith("/") || /^[a-zA-Z]:\\/.test(input)) {
    const localPath = resolve(process.cwd(), input);
    if (!existsSync(localPath)) {
      throw new Error(`Local path not found: ${localPath}`);
    }
    return { type: "local", localPath };
  }

  if (input.startsWith("http://") || input.startsWith("https://")) {
    const url = new URL(input);
    if (isGitHubHost(url.hostname)) {
      const [, owner, repo, , ref] = url.pathname.split("/");
      return { type: "github", owner, repo: repo?.replace(/\.git$/, ""), ref, cloneUrl: input };
    }
    if (url.hostname.includes("gitlab.com")) {
      const [, owner, repo] = url.pathname.split("/");
      return { type: "gitlab", owner, repo, cloneUrl: input };
    }
    return { type: "url", cloneUrl: input };
  }

  const parts = input.split("/");
  if (parts.length === 2) {
    const atIndex = parts[1].indexOf("@");
    if (atIndex > 0) {
      const repo = parts[1].substring(0, atIndex);
      const skillFilter = parts[1].substring(atIndex + 1);
      return { type: "github", owner: parts[0], repo, skillFilter };
    }
    return { type: "github", owner: parts[0], repo: parts[1] };
  }

  throw new Error(`Invalid source format: ${input}. Use owner/repo, local path, or URL.`);
}

export function getCloneUrl(source: ParsedSource): string {
  if (source.cloneUrl) return source.cloneUrl;
  if (source.type === "github" && source.owner && source.repo) {
    const ghHost = getGitHubHost();
    return `https://${ghHost}/${source.owner}/${source.repo}.git`;
  }
  throw new Error("Cannot determine clone URL");
}
