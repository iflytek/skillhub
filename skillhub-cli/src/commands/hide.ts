import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig } from "../core/config.js";
import { success, error, info } from "../utils/logger.js";

export function registerHide(program: Command) {
  const hideCmd = program
    .command("hide <slug>")
    .description("Hide a skill (admin only)")
    .option("--namespace <ns>", "Namespace", "global")
    .option("-y, --yes", "Skip confirmation")
    .action(async (slug: string, opts: { namespace: string; yes?: boolean }) => {
      if (!opts.yes) {
        const { createInterface } = await import("node:readline");
        const rl = createInterface({ input: process.stdin, output: process.stdout });
        const answer = await new Promise<string>((r) =>
          rl.question(`Hide ${slug} from ${opts.namespace}? [y/N] `, r)
        );
        rl.close();
        if (answer.toLowerCase() !== "y") {
          console.log("Cancelled.");
          return;
        }
      }

      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });

        const detail = await client.get<{ id: number }>(
          `/api/v1/skills/${opts.namespace}/${slug}`
        );

        await client.post(`/api/v1/admin/skills/${detail.id}/hide`, {
          body: JSON.stringify({}),
          headers: { "Content-Type": "application/json" },
        });

        success(`Hidden ${slug}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });

  hideCmd
    .command("unhide <slug>")
    .description("Unhide a skill (admin only)")
    .option("--namespace <ns>", "Namespace", "global")
    .option("-y, --yes", "Skip confirmation")
    .action(async (slug: string, opts: { namespace: string; yes?: boolean }) => {
      if (!opts.yes) {
        const { createInterface } = await import("node:readline");
        const rl = createInterface({ input: process.stdin, output: process.stdout });
        const answer = await new Promise<string>((r) =>
          rl.question(`Unhide ${slug} from ${opts.namespace}? [y/N] `, r)
        );
        rl.close();
        if (answer.toLowerCase() !== "y") {
          console.log("Cancelled.");
          return;
        }
      }

      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });

        const detail = await client.get<{ id: number }>(
          `/api/v1/skills/${opts.namespace}/${slug}`
        );

        await client.post(`/api/v1/admin/skills/${detail.id}/unhide`, {
          body: JSON.stringify({}),
          headers: { "Content-Type": "application/json" },
        });

        success(`Unhidden ${slug}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
