import { Command } from "commander";
import { removeToken } from "../core/auth-token.js";
import { success } from "../utils/logger.js";

export function registerLogout(program: Command) {
  program
    .command("logout")
    .description("Remove stored authentication token")
    .action(async () => {
      await removeToken();
      success("Logged out successfully");
    });
}
