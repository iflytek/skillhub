import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes, WhoamiResponse } from "../schema/routes.js";
import { requireToken } from "../core/auth-token.js";
import { success, error } from "../utils/logger.js";
import { loadConfig, loadConfigFromProgram } from "../core/config.js";

export function registerWhoami(program: Command) {
  program
    .command("whoami")
    .description("Show current authenticated user")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfigFromProgram(program);
        const client = new ApiClient({ baseUrl: config.registry, token });
        const resp = await client.get<WhoamiResponse>(ApiRoutes.whoami);
        const isJson = program.opts().json;
        if (isJson) {
          console.log(JSON.stringify(resp, null, 2));
        } else {
          console.log(`Handle:      ${resp.user.handle}`);
          console.log(`Display Name: ${resp.user.displayName}`);
        }
      } catch (e: any) {
        error(e.message);
        process.exitCode = 1;
      }
    });
}
