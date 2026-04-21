import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error } from "../utils/logger.js";
import { parseSkillName } from "../core/skill-name.js";

export function registerStar(program: Command) {
  program
    .command("star <slug>")
    .description("Star a skill")
    .option("--unstar", "Remove star")
    .action(async (slug: string, opts: { unstar: boolean }) => {
      try {
        const { namespace, slug: skillSlug } = parseSkillName(slug);
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });

        const detailPath = ApiRoutes.skillDetail.replace("{namespace}", namespace).replace("{slug}", skillSlug);
        const detail = await client.get<{ id: number }>(detailPath);

        const starPath = `/api/v1/skills/${detail.id}/star`;
        if (opts.unstar) {
          await client.delete(starPath);
          success(`Unstarred ${skillSlug}`);
        } else {
          await client.put(starPath);
          success(`Starred ${skillSlug}`);
        }
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });
}
