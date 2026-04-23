import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error } from "../utils/logger.js";


export function registerHide(program: Command) {
  const hideCmd = program
    .command("hide")
    .description("Hide a skill (admin only)")
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
          rl.question(`Hide ${skillSlug} from ${namespace}? [y/N] `, r)
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

        const detail = await client.get<{ id: number }>(
          `/api/v1/skills/${namespace}/${skillSlug}`
        );

        await client.post(`/api/v1/admin/skills/${detail.id}/hide`, {
          body: JSON.stringify({}),
          headers: { "Content-Type": "application/json" },
        });

        success(`Hidden ${skillSlug}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });

  hideCmd
    .command("unhide")
    .description("Unhide a skill (admin only)")
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
          rl.question(`Unhide ${skillSlug} from ${namespace}? [y/N] `, r)
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

        const detail = await client.get<{ id: number }>(
          `/api/v1/skills/${namespace}/${skillSlug}`
        );

        await client.post(`/api/v1/admin/skills/${detail.id}/unhide`, {
          body: JSON.stringify({}),
          headers: { "Content-Type": "application/json" },
        });

        success(`Unhidden ${skillSlug}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });
}
