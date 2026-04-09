import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { ApiRoutes, SearchResponse } from "../schema/routes.js";
import { info, dim } from "../utils/logger.js";
import { execSync } from "node:child_process";
import * as readline from "readline";

function parseNamespace(slug: string): { namespace: string; name: string } {
  const parts = slug.split("--");
  if (parts.length >= 2) {
    return { namespace: parts[0], name: parts.slice(1).join("--") };
  }
  return { namespace: "global", name: slug };
}

interface SearchSkill {
  name: string;
  slug: string;
  namespace: string;
  version?: string;
  summary?: string;
  installs?: number;
}

async function searchSkills(
  client: ApiClient,
  query: string,
  limit: number = 10
): Promise<SearchSkill[]> {
  const result = await client.get<SearchResponse>(
    `${ApiRoutes.search}?q=${encodeURIComponent(query)}&limit=${limit}`
  );

  if (!result.results || result.results.length === 0) {
    return [];
  }

  return result.results.map((s) => {
    const { namespace, name } = parseNamespace(s.slug);
    return {
      name,
      slug: s.slug,
      namespace,
      version: s.version,
      summary: s.summary,
      installs: s.installCount || 0,
    };
  }).sort((a, b) => (b.installs || 0) - (a.installs || 0));
}

async function runInteractiveSearch(
  client: ApiClient,
  initialQuery: string = ""
): Promise<string | null> {
  const MAX_VISIBLE = 8;
  let query = initialQuery;
  let results: SearchSkill[] = [];
  let selectedIndex = 0;
  let cursorRow = 0;

  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });

  const width = process.stdout.columns || 80;

  function clearLines(n: number): void {
    for (let i = 0; i < n; i++) {
      process.stdout.write(`\x1B[2K\x1B[${0}G`);
      if (i < n - 1) process.stdout.write("\n\x1B[1A");
    }
    if (n > 0) {
      process.stdout.write(`\x1B[${n}A`);
    }
  }

  async function render(): Promise<void> {
    clearLines(cursorRow + 2);

    const searchLine = `\x1B[1m? Search skills:\x1B[0m ${query || "(type to search)"}`;
    process.stdout.write(`${searchLine}\n`);

    const visibleResults = results.slice(0, MAX_VISIBLE);
    for (let i = 0; i < visibleResults.length; i++) {
      const skill = visibleResults[i];
      const isSelected = i === selectedIndex;
      const prefix = isSelected ? `\x1B[36m>\x1B[0m ` : "  ";
      const nameStr = isSelected ? `\x1B[1m${skill.name}\x1B[0m` : skill.name;
      const installStr = skill.installs ? ` (${skill.installs} installs)` : "";
      const summaryStr = skill.summary ? ` - ${skill.summary.slice(0, 40)}` : "";
      const line = `${prefix}${nameStr}\x1B[2m${installStr}${summaryStr}\x1B[0m`;
      const truncated = line.length > width - 2 ? line.slice(0, width - 5) + "..." : line;
      process.stdout.write(`  ${truncated}\n`);
    }

    if (results.length === 0 && query.length > 0) {
      process.stdout.write(`\x1B[2m  No results found\x1B[0m\n`);
    }

    cursorRow = Math.min(selectedIndex, visibleResults.length - 1, MAX_VISIBLE - 1);
    if (results.length === 0) cursorRow = 0;

    dim(`\n  ↑↓ navigate · Enter select · Esc cancel`);
  }

  let searchTimeout: NodeJS.Timeout | null = null;

  function triggerSearch(q: string): void {
    if (searchTimeout) clearTimeout(searchTimeout);
    searchTimeout = setTimeout(async () => {
      results = await searchSkills(client, q);
      selectedIndex = 0;
      await render();
    }, 150);
  }

  process.stdin.setRawMode?.(true);
  process.stdin.resume?.();
  readline.emitKeypressEvents(process.stdin);

  if (initialQuery) {
    query = initialQuery;
    results = await searchSkills(client, query);
    selectedIndex = 0;
  }

  await render();

  return new Promise<string | null>((resolve) => {
    const handler = (str: string, key: { name?: string }) => {
      if (key.name === "escape") {
        cleanup();
        resolve(null);
        return;
      }

      if (key.name === "up" || key.name === "k") {
        selectedIndex = Math.max(0, selectedIndex - 1);
        render();
        return;
      }

      if (key.name === "down" || key.name === "j") {
        selectedIndex = Math.min(Math.min(results.length, MAX_VISIBLE) - 1, selectedIndex + 1);
        render();
        return;
      }

      if (key.name === "return" || key.name === "enter") {
        if (results.length > 0 && selectedIndex < results.length) {
          cleanup();
          resolve(`${results[selectedIndex].namespace}/${results[selectedIndex].name}`);
        } else {
          cleanup();
          resolve(null);
        }
        return;
      }

      if (key.name === "backspace") {
        query = query.slice(0, -1);
        render();
        if (query.length > 0) {
          triggerSearch(query);
        } else {
          results = [];
          selectedIndex = 0;
          render();
        }
        return;
      }

      if (str && str.length === 1 && !key.ctrl && !key.meta) {
        query += str;
        render();
        triggerSearch(query);
      }
    };

    function cleanup(): void {
      process.stdin.setRawMode?.(false);
      process.stdin.pause?.();
      rl.close();
      process.stdin.removeListener("keypress", handler);
    }

    process.stdin.on("keypress", handler);
  });
}

export function registerExplore(program: Command) {
  program
    .command("explore")
    .aliases(["find"])
    .description("Browse or search skills from the registry")
    .argument("[query]", "Search query for finding skills")
    .option("-n, --limit <n>", "Max results", "20")
    .option("-i, --install", "Interactive select and install")
    .action(async (query: string | undefined, opts: { limit: string; install?: boolean }) => {
      const config = loadConfig();
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      try {
        if (!query) {
          const selected = await runInteractiveSearch(client);
          if (!selected) {
            console.log("\nCancelled.");
            return;
          }

          if (opts.install) {
            console.log(`\nInstalling ${selected}...`);
            execSync(`skillhub install ${selected}`, { stdio: "inherit" });
          } else {
            info(`\nSelected: ${selected}`);
            dim("Use --install or -i to install, or run: skillhub inspect " + selected);
          }
          return;
        }

        const results = await searchSkills(client, query, parseInt(opts.limit, 10));

        if (results.length === 0) {
          console.log(`\nNo skills found matching: ${query}`);
          return;
        }

        console.log(`\n\x1B[1mSkills matching "${query}"\x1B[0m (${results.length}):\n`);
        for (const skill of results) {
          console.log(`\x1B[1m${skill.name}\x1B[0m  [${skill.namespace}]`);
          dim(`  version · ${skill.version || "N/A"}`);
          if (skill.summary) console.log(`  ${skill.summary}`);
          console.log("");
        }

        dim("Tip: Use skillhub install <slug> to install, or skillhub explore --install for interactive mode");
        console.log("");
      } catch (e: any) {
        console.log(`Error: ${e.message}`);
      }
    });
}
