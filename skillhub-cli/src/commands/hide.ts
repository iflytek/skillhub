import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error } from "../utils/logger.js";
import { parseSkillName } from "../core/skill-name.js";

export function registerHide(program: Command) {
  const hideCmd = program
    .command("hide <slug>")
    .description("Hide a skill (admin only)")
    .option("-y, --yes", "Skip confirmation")
    .action(async (slug: string, opts: { yes?: boolean }) => {
      const { namespace, slug: skillSlug } = parseSkillName(slug);
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
        process.exit(1);
      }
    });

  hideCmd
    .command("unhide <slug>")
    .description("Unhide a skill (admin only)")
    .option("-y, --yes", "Skip confirmation")
    .action(async (slug: string, opts: { yes?: boolean }) => {
      const { namespace, slug: skillSlug } = parseSkillName(slug);
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
        process.exit(1);
      }
    });
}
