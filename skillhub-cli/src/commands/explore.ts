import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { ApiRoutes, SearchResponse } from "../schema/routes.js";
import { info, dim } from "../utils/logger.js";

function parseNamespace(slug: string): { namespace: string; name: string } {
  const parts = slug.split("--");
  if (parts.length >= 2) {
    return { namespace: parts[0], name: parts.slice(1).join("--") };
  }
  return { namespace: "global", name: slug };
}

export function registerExplore(program: Command) {
  program
    .command("explore")
    .description("Browse latest updated skills from the registry")
    .option("-n, --limit <n>", "Max results", "20")
    .action(async (opts: { limit: string }) => {
      const config = loadConfig();
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      try {
        const result = await client.get<SearchResponse>(
          `${ApiRoutes.search}?q=&limit=${opts.limit}`
        );
        
        if (!result.results || result.results.length === 0) {
          console.log("No skills found.");
          return;
        }
        
        if (program.opts().json) {
          console.log(JSON.stringify(result.results, null, 2));
          return;
        }
        
        console.log("");
        info(`Latest Skills (${result.results.length}):`);
        console.log("");
        for (const s of result.results) {
          const { namespace, name } = parseNamespace(s.slug);
          console.log(`\x1b[1m${name}\x1b[0m  [${namespace}]`);
          dim(`  version · ${s.version}`);
          if (s.summary) console.log(`  ${s.summary}`);
          console.log("");
        }
      } catch (e: any) {
        console.log(`Error: ${e.message}`);
      }
    });
}
