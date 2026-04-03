import { Command } from "commander";
import { existsSync, readdirSync, statSync, unlinkSync, rmdirSync } from "node:fs";
import { join } from "node:path";
import { getAllAgents } from "../core/agent-detector.js";
import { success, error, info } from "../utils/logger.js";

export function registerList(program: Command) {
  program
    .command("list")
    .alias("ls")
    .description("List installed skills")
    .option("--global", "List global skills only")
    .option("--project", "List project skills only")
    .action((opts: { global?: boolean; project?: boolean }) => {
      const agents = getAllAgents();
      let found = false;

      for (const agent of agents) {
        const dirs = [];
        if (!opts.global) dirs.push(join(process.cwd(), agent.projectPath));
        if (!opts.project) dirs.push(join(process.env.HOME || "", agent.globalPath));

        for (const dir of dirs) {
          if (!existsSync(dir)) continue;
          const skills = readdirSync(dir).filter((f) => {
            const full = join(dir, f);
            return statSync(full).isDirectory() && existsSync(join(full, "SKILL.md"));
          });

          if (skills.length > 0) {
            found = true;
            const scope = dir.includes(process.cwd()) ? "project" : "global";
            info(`\n${agent.name} (${scope}):`);
            for (const s of skills) {
              console.log(`  ${s}`);
            }
          }
        }
      }

      if (!found) {
        console.log("No skills installed.");
      }
    });
}
