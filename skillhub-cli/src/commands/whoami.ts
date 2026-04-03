import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes, WhoamiResponse } from "../schema/routes.js";
import { requireToken } from "../core/auth-token.js";
import { success, error } from "../utils/logger.js";
import { loadConfig } from "../core/config.js";

export function registerWhoami(program: Command) {
  program
    .command("whoami")
    .description("Show current authenticated user")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });
        const user = await client.get<WhoamiResponse>(ApiRoutes.whoami);
        console.log(`User ID:     ${user.userId}`);
        console.log(`Display Name: ${user.displayName}`);
        if (user.email) console.log(`Email:       ${user.email}`);
      } catch (e: any) {
        error(`Not authenticated: ${e.message}`);
        process.exit(1);
      }
    });
}
