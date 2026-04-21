import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { ApiRoutes } from "../schema/routes.js";
import { info, dim } from "../utils/logger.js";
import * as readline from "readline";
import { searchSkills, type SearchSkill } from "../core/interactive-search.js";

const HIDE_CURSOR = "\x1b[?25l";
const SHOW_CURSOR = "\x1b[?25h";
const CLEAR_DOWN = "\x1b[J";
const MOVE_UP = (n: number) => `\x1b[${n}A`;
const MOVE_TO_COL = (n: number) => `\x1b[${n}G`;

const RESET = "\x1b[0m";
const BOLD = "\x1b[1m";
const DIM = "\x1b[38;5;102m";
const TEXT = "\x1b[38;5;145m";
const CYAN = "\x1b[36m";
const YELLOW = "\x1b[33m";
const GREEN = "\x1b[32m";

function formatInstalls(count: number): string {
  if (!count || count <= 0) return "";
  if (count >= 1_000_000) return `${(count / 1_000_000).toFixed(1).replace(/\.0$/, "")}M installs`;
  if (count >= 1_000) return `${(count / 1_000).toFixed(1).replace(/\.0$/, "")}K installs`;
  return `${count} install${count === 1 ? "" : "s"}`;
}

interface SkillDetail {
  starCount: number;
  downloadCount: number;
  version: string;
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

async function runInteractiveSearch(
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
    const searchLine = `${TEXT}Search skills:${RESET} ${query}${cursor}`;
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
        const loadingIndicator = loading && i === 0 ? ` ${DIM}...${RESET}` : "";

        lines.push(`  ${arrow} ${name}${nsBadge}${versionBadge}${loadingIndicator}`);
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

export function registerExplore(program: Command) {
  program
    .command("explore")
    .aliases(["find", "find-skills", "search"])
    .description("Browse or search skills from the registry")
    .argument("[query]", "Search query for finding skills")
    .option("-n, --limit <n>", "Max results", "20")
    .option("-s, --sort <sort>", "Sort by: hot, newest, downloads (default: interactive mode)")
    .option("--hot", "Sort by popularity (shorthand for --sort hot)")
    .option("--newest", "Sort by newest first (shorthand for --sort newest)")
    .option("--downloads", "Sort by download count (shorthand for --sort downloads)")
    .action(async (query: string | undefined, opts: { limit: string; sort?: string; hot?: boolean; newest?: boolean; downloads?: boolean }) => {
      const config = loadConfigFromProgram(program);
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });
      const sortMap: Record<string, string> = { hot: "rating", newest: "newest", downloads: "downloads" };
      
      // Resolve sort priority: explicit --sort > shorthand flags > default
      let effectiveSort = opts.sort;
      if (!effectiveSort) {
        if (opts.hot) effectiveSort = "hot";
        else if (opts.newest) effectiveSort = "newest";
        else if (opts.downloads) effectiveSort = "downloads";
      }
      const apiSort = sortMap[effectiveSort || "newest"] || "newest";

      try {
        // Enter interactive mode only if no query AND no sort option (explicit or shorthand)
        const hasSortOption = opts.sort || opts.hot || opts.newest || opts.downloads;
        if (!query && !hasSortOption) {
          const selected = await runInteractiveSearch(client, "", apiSort);
          if (!selected) {
            console.log("\nCancelled.");
            return;
          }
          info(`\nSelected: ${selected}`);
          dim("Run: skillhub install " + selected);
          return;
        }

        const results = await searchSkills(client, query || "", parseInt(opts.limit, 10), apiSort);

        if (results.length === 0) {
          console.log(`${DIM}No skills found${RESET}`);
          return;
        }

        const maxResults = Math.min(results.length, parseInt(opts.limit, 10));

        const detailPromises = results.slice(0, maxResults).map((s) =>
          fetchSkillDetail(client, s.namespace, s.name)
        );
        const details = await Promise.all(detailPromises);

        console.log(`${DIM}Install with${RESET} skillhub install <slug>`);
        console.log();

        for (let i = 0; i < maxResults; i++) {
          const skill = results[i]!;
          const detail = details[i];
          const slug = `${skill.namespace}--${skill.name}`;
          const nsBadge = skill.namespace !== "global" ? ` ${YELLOW}[${skill.namespace}]${RESET}` : "";
          const stars = detail?.starCount ? ` ${YELLOW}⭐ ${detail.starCount}${RESET}` : "";
          const downloads = detail?.downloadCount ? ` ${CYAN}↓ ${formatInstalls(detail.downloadCount)}${RESET}` : "";

          console.log(`${TEXT}${skill.name}${RESET}${nsBadge}${stars}${downloads}`);
          console.log(`${DIM}└ skillhub install ${skill.namespace}/${skill.name}${RESET}`);
          if (skill.summary) {
            console.log(`${DIM}  ${skill.summary.slice(0, 60)}${RESET}`);
          }
          console.log();
        }

        dim("Tip: Use skillhub explore without args for interactive mode");
        console.log("");
      } catch (e: any) {
        console.log(`Error: ${e.message}`);
      }
    });
}
