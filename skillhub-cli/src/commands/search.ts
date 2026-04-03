import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes, SearchResponse } from "../schema/routes.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { error } from "../utils/logger.js";

export function registerSearch(program: Command) {
  program
    .command("search <query...>")
    .description("Search for skills on SkillHub")
    .option("-n, --limit <n>", "Max results", "20")
    .action(async (query: string[], opts: { limit: string }) => {
      const config = loadConfig();
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      try {
        const result = await client.get<SearchResponse>(
          `${ApiRoutes.search}?q=${encodeURIComponent(query.join(" "))}&limit=${opts.limit}`
        );
        if (!result.results || result.results.length === 0) {
          console.log("No skills found.");
          return;
        }
        for (const s of result.results) {
          console.log(`${s.slug} (${s.version}) — ${s.displayName}`);
          if (s.summary) console.log(`  ${s.summary}`);
        }
      } catch (e: any) {
        error(`Search failed: ${e.message}`);
        process.exit(1);
      }
    });
}
