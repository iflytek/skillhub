import { Command } from "commander";
import { listAction } from "./list.js";

export function registerCheck(program: Command) {
  program
    .command("check")
    .description("Check installed skills (alias for 'list --status managed,missing')")
    .option("--scope <scope>", "Scope to check (global, project, all)")
    .option("--agent <agents...>", "Filter by specific agents")
    .option("--json", "Output results as JSON")
    .action(async (opts: {
      scope?: string;
      agent?: string[];
      json?: boolean;
    }) => {
      console.log("Tip: Use 'skillhub list' for more options including orphaned skills");
      console.log("");

      await listAction({
        ...opts,
        status: ["managed", "missing"],
      });
    });
}
