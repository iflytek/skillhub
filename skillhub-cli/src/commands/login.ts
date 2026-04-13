import { Command } from "commander";
import { createInterface } from "node:readline";
import { stdin, stdout } from "node:process";
import { writeToken } from "../core/auth-token.js";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes, WhoamiResponse } from "../schema/routes.js";
import { success, error, info } from "../utils/logger.js";

export function registerLogin(program: Command) {
  program
    .command("login")
    .description("Authenticate with SkillHub registry")
    .option("--token <token>", "Auth token (skipped prompt)")
    .option("--registry <url>", "Registry URL override")
    .action(async (opts: { token?: string; registry?: string }) => {
      const rl = createInterface({ input: stdin, output: stdout });
      const ask = (q: string) => new Promise<string>((r) => rl.question(q, r));

      const token = opts.token || (await ask("Enter your SkillHub token: "));
      rl.close();

      const registry = opts.registry || "http://localhost:8080";
      const client = new ApiClient({ baseUrl: registry, token });

      try {
        const resp = await client.get<WhoamiResponse>(ApiRoutes.whoami);
        await writeToken(token);
        success(`Authenticated as ${resp.user.displayName} (@${resp.user.handle})`);
      } catch (e: any) {
        error(`Authentication failed: ${e.message}`);
        process.exit(1);
      }
    });
}
