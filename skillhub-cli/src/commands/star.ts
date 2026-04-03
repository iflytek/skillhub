import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig } from "../core/config.js";
import { success, error } from "../utils/logger.js";

export function registerStar(program: Command) {
  program
    .command("star <slug>")
    .description("Star a skill")
    .option("--namespace <ns>", "Namespace", "global")
    .option("--unstar", "Remove star")
    .action(async (slug: string, opts: { namespace: string; unstar: boolean }) => {
      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });
        const path = `${ApiRoutes.skillStar.replace("{namespace}", opts.namespace).replace("{slug}", slug)}`;

        if (opts.unstar) {
          await client.delete(path);
          success(`Unstarred ${slug}`);
        } else {
          await client.post(path);
          success(`Starred ${slug}`);
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
