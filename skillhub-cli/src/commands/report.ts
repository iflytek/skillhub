import { Command } from "commander";
import { createInterface } from "node:readline";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error } from "../utils/logger.js";
import { parseSkillName } from "../core/skill-name.js";

export function registerReport(program: Command) {
  program
    .command("report <slug>")
    .description("Report a skill for review")
    .option("--reason <reason>", "Report reason")
    .action(async (slug: string, opts: { reason?: string }) => {
      try {
        const { namespace, slug: skillSlug } = parseSkillName(slug);
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });

        let reason = opts.reason;
        if (!reason) {
          const rl = createInterface({ input: process.stdin, output: process.stdout });
          reason = await new Promise<string>((r) =>
            rl.question("Report reason: ", r)
          );
          rl.close();
        }

        await client.post(`/api/v1/skills/${namespace}/${skillSlug}/reports`, {
          body: JSON.stringify({ reason }),
          headers: { "Content-Type": "application/json" },
        });
        success(`Report submitted for ${skillSlug}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
