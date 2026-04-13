import { Command } from "commander";
import { ApiClient } from "../core/api-client.js";
import { ApiRoutes, NamespaceResponse } from "../schema/routes.js";
import { requireToken } from "../core/auth-token.js";
import { loadConfig } from "../core/config.js";
import { error } from "../utils/logger.js";

export function registerNamespaces(program: Command) {
  program
    .command("namespaces")
    .description("List namespaces you have access to")
    .action(async () => {
      try {
        const token = await requireToken();
        const config = loadConfig();
        const client = new ApiClient({ baseUrl: config.registry, token });
        const namespaces = await client.get<NamespaceResponse[]>(ApiRoutes.meNamespaces);
        const isJson = program.opts().json;
        if (isJson) {
          console.log(JSON.stringify(namespaces, null, 2));
        } else {
          if (!namespaces || namespaces.length === 0) {
            console.log("No namespaces found.");
            return;
          }
          for (const ns of namespaces) {
            console.log(`${ns.slug} — ${ns.displayName} [${ns.currentUserRole}] (${ns.status})`);
          }
        }
      } catch (e: any) {
        error(`Failed to list namespaces: ${e.message}`);
        process.exit(1);
      }
    });
}
