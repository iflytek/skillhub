import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes } from "../schema/routes.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";
import { success, error } from "../utils/logger.js";

export function registerTransfer(program: Command) {
  program
    .command("transfer <namespace> <newOwnerId>")
    .description("Transfer ownership of a namespace to another user")
    .option("-y, --yes", "Skip confirmation")
    .action(async (namespace: string, newOwnerId: string, opts: { yes?: boolean }) => {
      if (!opts.yes) {
        const { createInterface } = await import("node:readline");
        const rl = createInterface({ input: process.stdin, output: process.stdout });
        const answer = await new Promise<string>((r) =>
          rl.question(`Transfer ownership of ${namespace} to ${newOwnerId}? [y/N] `, r)
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
        await client.post(ApiRoutes.namespaceTransferOwnership.replace("{namespace}", namespace), { body: JSON.stringify({ newOwnerId }) });
        success(`Ownership of ${namespace} transferred to ${newOwnerId}`);
      } catch (e: any) {
        error(`Failed: ${e.message}`);
        process.exitCode = 1;
      }
    });
}