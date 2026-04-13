import { Command } from "commander";
import { existsSync, readdirSync, statSync, unlinkSync, rmdirSync } from "node:fs";
import { join } from "node:path";
import { getAllAgents, type AgentInfo } from "../core/agent-detector.js";
import { success, info } from "../utils/logger.js";
import { removeFromLock } from "../core/skill-lock.js";

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

async function uninstallSkill(
  name: string,
  agent: AgentInfo,
  scope: "local" | "global",
  yes: boolean
): Promise<boolean> {
  const baseDir = scope === "global"
    ? join(process.env.HOME || "", agent.globalPath)
    : join(process.cwd(), agent.projectPath);
  const skillPath = join(baseDir, name);

  if (!existsSync(skillPath)) return false;
  if (!statSync(skillPath).isDirectory()) return false;

  if (!yes) {
    const { createInterface } = await import("node:readline");
    const rl = createInterface({ input: process.stdin, output: process.stdout });
    const answer = await new Promise<string>((r) =>
      rl.question(`Uninstall ${name} from ${agent.name}? [y/N] `, r)
    );
    rl.close();
    if (answer.toLowerCase() !== "y") return false;
  }

  removeDir(skillPath);
  return true;
}

function discoverInstalledSkills(scope: "local" | "global", agent?: AgentInfo): string[] {
  const skills: string[] = [];
  const agents = agent ? [agent] : getAllAgents();

  for (const a of agents) {
    const baseDir = scope === "global"
      ? join(process.env.HOME || "", a.globalPath)
      : join(process.cwd(), a.projectPath);

    if (!existsSync(baseDir)) continue;

    try {
      for (const entry of readdirSync(baseDir)) {
        const skillPath = join(baseDir, entry);
        if (statSync(skillPath).isDirectory() && existsSync(join(skillPath, "SKILL.md"))) {
          skills.push(entry);
        }
      }
    } catch {}
  }

  return [...new Set(skills)];
}

export function registerUninstall(program: Command) {
  program
    .command("uninstall [name]")
    .alias("un")
    .description("Uninstall a skill or all skills from local agent")
    .option("--global", "Uninstall from global scope")
    .option("-a, --agent <agents...>", "Uninstall from specific agents")
    .option("-y, --yes", "Skip confirmation")
    .option("--all", "Uninstall all installed skills")
    .action(async (name: string | undefined, opts: { global?: boolean; agent?: string[]; yes?: boolean; all?: boolean }) => {
      const scope = opts.global ? "global" : "local";
      const targetAgents = opts.agent
        ? getAllAgents().filter((a) => opts.agent!.includes(a.key))
        : getAllAgents();

      if (opts.all) {
        const skills = discoverInstalledSkills(scope);

        if (skills.length === 0) {
          info("No skills installed.");
          return;
        }

        if (!opts.yes) {
          console.log(`\nSkills to uninstall:`);
          for (const skill of skills) {
            console.log(`  - ${skill}`);
          }
          console.log("");

          const { createInterface } = await import("node:readline");
          const rl = createInterface({ input: process.stdin, output: process.stdout });
          const answer = await new Promise<string>((r) =>
            rl.question(`Uninstall ${skills.length} skill(s)? [y/N] `, r)
          );
          rl.close();
          if (answer.toLowerCase() !== "y") {
            console.log("Cancelled.");
            return;
          }
        }

        let uninstalled = 0;
        for (const skill of skills) {
          for (const agent of targetAgents) {
            const ok = await uninstallSkill(skill, agent, scope, true);
            if (ok) uninstalled++;
          }
          await removeFromLock(skill);
        }

        success(`Uninstalled ${uninstalled} skill(s).`);
        return;
      }

      if (!name) {
        console.log("Error: specify a skill name or use --all");
        return;
      }

      let uninstalled = 0;
      for (const agent of targetAgents) {
        const ok = await uninstallSkill(name, agent, scope, !!opts.yes);
        if (ok) {
          uninstalled++;
          success(`Uninstalled ${name} from ${agent.name}`);
        }
      }

      if (uninstalled === 0) {
        info(`Skill "${name}" not found.`);
      }

      await removeFromLock(name);
    });
}
