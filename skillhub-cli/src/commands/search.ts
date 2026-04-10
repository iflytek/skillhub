import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes, SearchResponse } from "../schema/routes.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { error, dim } from "../utils/logger.js";

export function registerSearch(program: Command) {
  program
    .command("search <query...>")
    .description("[Deprecated: use 'explore' instead] Search for skills on SkillHub")
    .option("-n, --limit <n>", "Max results", "20")
    .option("--namespace <ns>", "Filter by namespace")
    .action(async (query: string[], opts: { limit: string; namespace?: string }) => {
      const config = loadConfig();
      const token = await readToken();
      const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

      const isJson = program.opts().json;

      try {
        let searchUrl = `${ApiRoutes.search}?q=${encodeURIComponent(query.join(" "))}&limit=${opts.limit}`;
        if (opts.namespace) {
          searchUrl += `&namespace=${encodeURIComponent(opts.namespace)}`;
        }
        const result = await client.get<SearchResponse>(searchUrl);
        if (!result.results || result.results.length === 0) {
          if (isJson) {
            console.log(JSON.stringify({ results: [] }));
          } else {
            console.log("No skills found.");
          }
          return;
        }

        if (isJson) {
          console.log(JSON.stringify(result, null, 2));
        } else {
          const hasNamespaceFilter = !!opts.namespace;
          for (const s of result.results) {
            const ns = s.namespace ? `[${s.namespace}] ` : '';
            console.log(`${ns}${s.slug} (${s.version}) — ${s.displayName}`);
            if (s.summary) console.log(`  ${s.summary}`);
          }
          if (hasNamespaceFilter) {
            dim(`\nTip: remove --namespace filter to search all namespaces`);
          }
        }
      } catch (e: any) {
        error(`Search failed: ${e.message}`);
        process.exit(1);
      }
    });
}
