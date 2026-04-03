import { Command } from "commander";
import { createInterface } from "node:readline";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig } from "../core/config.js";
import { success, error } from "../utils/logger.js";

export function registerReport(program: Command) {
  program
    .command("report <slug>")
    .description("Report a skill for review")
    .option("--namespace <ns>", "Namespace", "global")
    .option("--reason <reason>", "Report reason")
    .action(async (slug: string, opts: { namespace: string; reason?: string }) => {
      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });

        let reason = opts.reason;
        if (!reason) {
          const rl = createInterface({ input: process.stdin, output: process.stdout });
          reason = await new Promise<string>((r) =>
            rl.question("Report reason: ", r)
          );
          rl.close();
        }

        await client.post(`/api/v1/skills/${opts.namespace}/${slug}/reports`, {
          body: JSON.stringify({ reason }),
          headers: { "Content-Type": "application/json" },
        });
        success(`Report submitted for ${slug}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
