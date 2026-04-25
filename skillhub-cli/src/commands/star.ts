import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error, dim } from "../utils/logger.js";


export function registerStar(program: Command) {
  program
    .command("star")
    .description("Star a skill")
    .argument("<skill>", "Skill name or namespace/skill-name")
    .option("--unstar", "Remove star")
    .option("--namespace <ns>", "Override namespace (default: parsed from skill or 'global')")
    .action(async (slug: string, opts: { unstar: boolean; namespace?: string }) => {
      try {
        const { parseSkillNamespace } = await import("../core/skill-resolver.js");
        const { namespace, slug: skillSlug } = parseSkillNamespace(slug, opts.namespace);
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
        const status = e.status || e.statusCode;
        if (status === 404) {
          error(`Skill not found: ${namespace}/${skillSlug}`);
          if (!slug.includes("/")) {
            dim("Tip: Use namespace/skill-name format, e.g., vision2group/docker-build-push");
          }
        } else {
          error(`Failed: ${e.message}`);
        }
        process.exitCode = 1;
      }
    });
}
