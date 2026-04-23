import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error } from "../utils/logger.js";
export function registerDelete(program: Command) {
  program
    .command("delete")
    .description("Delete a skill you own")
    .argument("<skill>", "Skill name or namespace/skill-name")
    .option("-y, --yes", "Skip confirmation")
    .option("--namespace <ns>", "Override namespace (default: parsed from skill or 'global')")
    .action(async (slug: string, opts: { yes?: boolean; namespace?: string }) => {
      const { parseSkillNamespace } = await import("../core/skill-resolver.js");
      const { namespace, slug: skillSlug } = parseSkillNamespace(slug, opts.namespace);
      if (!opts.yes) {
        const { createInterface } = await import("node:readline");
        const rl = createInterface({ input: process.stdin, output: process.stdout });
        const answer = await new Promise<string>((r) =>
          rl.question(`Delete ${skillSlug} from ${namespace}? This cannot be undone. [y/N] `, r)
        );
        rl.close();
        if (answer.toLowerCase() !== "y") {
          console.log("Cancelled.");
          return;
        }
      }

      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });
        await client.delete(`/api/v1/skills/${namespace}/${skillSlug}`);
        success(`Deleted ${skillSlug} from ${namespace}`);
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
