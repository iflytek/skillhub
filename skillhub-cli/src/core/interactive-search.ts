import { ApiClient } from "./api-client.js";
import { ApiRoutes, SearchResponse, SkillsListResponse } from "../schema/routes.js";
import * as readline from "readline";
import { dim, info } from "../utils/logger.js";

const HIDE_CURSOR = "\x1b[?25l";
const SHOW_CURSOR = "\x1b[?25h";
const CLEAR_DOWN = "\x1b[J";
const MOVE_UP = (n: number) => `\x1b[${n}A`;
const MOVE_TO_COL = (n: number) => `\x1b[${n}G`;

const RESET = "\x1b[0m";
const BOLD = "\x1b[1m";
const TEXT = "\x1b[38;5;145m";
const YELLOW = "\x1b[33m";
const DIM = "\x1b[38;5;102m";

export interface SearchSkill {
  name: string;
  slug: string;
  namespace: string;
  version?: string;
  summary?: string;
  installs?: number;
}

interface SkillDetail {
  starCount: number;
  downloadCount: number;
  version: string;
}

export function parseNamespace(slug: string): { namespace: string; name: string } {
  const parts = slug.split("--");
  if (parts.length >= 2) {
    return { namespace: parts[0], name: parts.slice(1).join("--") };
  }
  return { namespace: "global", name: slug };
}

function formatInstalls(count: number): string {
  if (!count || count <= 0) return "";
  if (count >= 1_000_000) return `${(count / 1_000_000).toFixed(1).replace(/\.0$/, "")}M installs`;
  if (count >= 1_000) return `${(count / 1_000).toFixed(1).replace(/\.0$/, "")}K installs`;
  return `${count} install${count === 1 ? "" : "s"}`;
}

async function fetchSkillDetail(client: ApiClient, namespace: string, name: string): Promise<SkillDetail | null> {
  try {
    const detail = await client.get<SkillDetail>(
      `${ApiRoutes.skillDetail.replace("{namespace}", namespace).replace("{slug}", name)}`
    );
    return detail;
  } catch {
    return null;
  }
}

export async function searchSkills(
  client: ApiClient,
  query: string,
  limit: number = 10,
  sort?: string
): Promise<SearchSkill[]> {
  if (!query) {
    const params = new URLSearchParams({ limit: limit.toString() });
    if (sort && sort !== "newest") {
      params.set("sort", sort);
    }
    const result = await client.get<SkillsListResponse>(
      `${ApiRoutes.skills}?${params.toString()}`
    );
    if (!result.items || result.items.length === 0) {
      return [];
    }
    const skills = result.items.map((s) => {
      const { namespace, name } = parseNamespace(s.slug);
      return {
        name,
        slug: s.slug,
        namespace,
        version: s.latestVersion?.version || "",
        summary: s.summary,
        installs: s.stats?.downloads || 0,
        stars: s.stats?.stars || 0,
        rating: s.ratingAvg || 0,
        updatedAt: s.updatedAt || 0,
      };
    });
    if (sort === "downloads") {
      return skills.sort((a, b) => b.installs - a.installs);
    } else if (sort === "stars") {
      return skills.sort((a, b) => b.stars - a.stars);
    } else {
      return skills.sort((a, b) => b.updatedAt - a.updatedAt);
    }
  }

  const params = new URLSearchParams({ q: query, limit: limit.toString() });
  if (sort) {
    params.set("sort", sort);
  }
  const result = await client.get<SearchResponse>(
    `${ApiRoutes.search}?${params.toString()}`
  );

  if (!result.results || result.results.length === 0) {
    return [];
  }

  const skills = result.results.map((s) => {
    const { namespace, name } = parseNamespace(s.slug);
    return {
      name,
      slug: s.slug,
      namespace,
      version: s.version,
      summary: s.summary,
      installs: s.downloadCount || 0,
      stars: s.starCount || 0,
      rating: s.ratingAvg || 0,
      updatedAt: s.updatedAt ? new Date(s.updatedAt).getTime() : 0,
    };
  });

  if (sort === "downloads") {
    return skills.sort((a, b) => b.installs - a.installs);
  } else if (sort === "stars") {
    return skills.sort((a, b) => b.stars - a.stars);
  } else {
    return skills.sort((a, b) => b.updatedAt - a.updatedAt);
  }
}

export async function runInteractiveSearch(
  client: ApiClient,
  initialQuery: string = "",
  sort: string = "newest"
): Promise<string | null> {
  const MAX_VISIBLE = 8;
  let query = initialQuery;
  let results: SearchSkill[] = [];
  let selectedIndex = 0;
  let loading = false;
  let lastRenderedLines = 0;
  let debounceTimer: ReturnType<typeof setTimeout> | null = null;

  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });

  const width = process.stdout.columns || 80;

  if (process.stdin.isTTY) {
    process.stdin.setRawMode(true);
  }
  process.stdin.resume();
  process.stdout.write(HIDE_CURSOR);

  function render(): void {
    if (lastRenderedLines > 0) {
      process.stdout.write(MOVE_UP(lastRenderedLines) + MOVE_TO_COL(1));
    }
    process.stdout.write(CLEAR_DOWN);

    const lines: string[] = [];

    const cursor = `${BOLD}_${RESET}`;
    const searchLine = `${TEXT}Select namespace:${RESET} ${query}${cursor}`;
    lines.push(searchLine);
    lines.push("");

    if (!query || query.length < 2) {
      lines.push(`${DIM}Start typing to search (min 2 chars)${RESET}`);
    } else if (results.length === 0 && loading) {
      lines.push(`${DIM}Searching...${RESET}`);
    } else if (results.length === 0) {
      lines.push(`${DIM}No skills found${RESET}`);
    } else {
      const visible = results.slice(0, MAX_VISIBLE);
      for (let i = 0; i < visible.length; i++) {
        const skill = visible[i]!;
        const isSelected = i === selectedIndex;
        const arrow = isSelected ? `${BOLD}>${RESET}` : " ";
        const name = isSelected ? `${BOLD}${skill.name}${RESET}` : `${TEXT}${skill.name}${RESET}`;
        const nsBadge = skill.namespace !== "global" ? ` ${YELLOW}[${skill.namespace}]${RESET}` : "";
        const versionBadge = skill.version ? ` ${DIM}v${skill.version}${RESET}` : "";

        lines.push(`  ${arrow} ${name}${nsBadge}${versionBadge}`);
      }
    }

    lines.push("");
    lines.push(`${DIM}up/down navigate | enter select | esc cancel${RESET}`);

    for (const line of lines) {
      process.stdout.write(line + "\n");
    }

    lastRenderedLines = lines.length;
  }

  function triggerSearch(q: string): void {
    if (debounceTimer) {
      clearTimeout(debounceTimer);
      debounceTimer = null;
    }

    loading = false;

    if (!q || q.length < 2) {
      results = [];
      selectedIndex = 0;
      render();
      return;
    }

    loading = true;
    render();

    const debounceMs = Math.max(150, 350 - q.length * 50);

    debounceTimer = setTimeout(async () => {
      try {
        results = await searchSkills(client, q, 10, sort);
        selectedIndex = 0;
      } catch {
        results = [];
      } finally {
        loading = false;
        debounceTimer = null;
        render();
      }
    }, debounceMs);
  }

  if (initialQuery) {
    triggerSearch(initialQuery);
  }
  render();

  return new Promise<string | null>((resolve) => {
    function cleanup(): void {
      process.stdin.removeListener("keypress", handleKeypress);
      if (process.stdin.isTTY) {
        process.stdin.setRawMode(false);
      }
      process.stdout.write(SHOW_CURSOR);
      process.stdin.pause();
      rl.close();
    }

    function handleKeypress(_ch: string | undefined, key: readline.Key): void {
      if (!key) return;

      if (key.name === "escape" || (key.ctrl && key.name === "c")) {
        cleanup();
        resolve(null);
        return;
      }

      if (key.name === "return") {
        cleanup();
        resolve(results[selectedIndex] ? `${results[selectedIndex].namespace}/${results[selectedIndex].name}` : null);
        return;
      }

      if (key.name === "up") {
        selectedIndex = Math.max(0, selectedIndex - 1);
        render();
        return;
      }

      if (key.name === "down") {
        selectedIndex = Math.min(Math.max(0, results.length - 1), selectedIndex + 1);
        render();
        return;
      }

      if (key.name === "backspace") {
        if (query.length > 0) {
          query = query.slice(0, -1);
          triggerSearch(query);
        }
        return;
      }

      if (key.sequence && !key.ctrl && !key.meta && key.sequence.length === 1) {
        const char = key.sequence;
        if (char >= " " && char <= "~") {
          query += char;
          triggerSearch(query);
        }
      }
    }

    process.stdin.on("keypress", handleKeypress);
  });
}
