import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig } from "../core/config.js";
import { success, error } from "../utils/logger.js";

export function registerArchive(program: Command) {
  program
    .command("archive <slug>")
    .description("Archive a skill you own")
    .option("--namespace <ns>", "Namespace", "global")
    .option("-y, --yes", "Skip confirmation")
    .action(async (slug: string, opts: { namespace: string; yes?: boolean }) => {
      if (!opts.yes) {
        const { createInterface } = await import("node:readline");
        const rl = createInterface({ input: process.stdin, output: process.stdout });
        const answer = await new Promise<string>((r) =>
          rl.question(`Archive ${slug} from ${opts.namespace}? [y/N] `, r)
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
        await client.post(`/api/v1/skills/${opts.namespace}/${slug}/archive`);
        success(`Archived ${slug}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exit(1);
      }
    });
}
