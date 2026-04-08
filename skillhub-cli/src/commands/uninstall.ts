import { Command } from "commander";
import { existsSync, readdirSync, statSync, unlinkSync, rmdirSync } from "node:fs";
import { join } from "node:path";
import { getAllAgents } from "../core/agent-detector.js";
import { success, error, info } from "../utils/logger.js";

function removeDir(path: string) {
  const stat = statSync(path);
  if (stat.isDirectory()) {
    for (const entry of readdirSync(path)) {
      removeDir(join(path, entry));
    }
    rmdirSync(path);
  } else {
    unlinkSync(path);
  }
}

export function registerUninstall(program: Command) {
  program
    .command("uninstall <name>")
    .alias("un")
    .description("Uninstall a skill from local agent")
    .option("--global", "Uninstall from global scope")
    .option("-y, --yes", "Skip confirmation")
    .action(async (name: string, opts: { global?: boolean; yes?: boolean }) => {
      const agents = getAllAgents();
      let uninstalled = 0;

      for (const agent of agents) {
        const dirs: string[] = [];
        if (!opts.global) dirs.push(join(process.cwd(), agent.projectPath));
        dirs.push(join(process.env.HOME || "", agent.globalPath));

        for (const dir of dirs) {
          const skillPath = join(dir, name);
          if (!existsSync(skillPath)) continue;
          if (!statSync(skillPath).isDirectory()) continue;

          if (!opts.yes) {
            const { createInterface } = await import("node:readline");
            const rl = createInterface({ input: process.stdin, output: process.stdout });
            const answer = await new Promise<string>((r) =>
              rl.question(`Uninstall ${name} from ${agent.name}? [y/N] `, r)
            );
            rl.close();
            if (answer.toLowerCase() !== "y") continue;
          }

          removeDir(skillPath);
          uninstalled++;
          success(`Uninstalled ${name} from ${agent.name}`);
        }
      }

      if (uninstalled === 0) {
        info(`Skill "${name}" not found.`);
      }
    });
}