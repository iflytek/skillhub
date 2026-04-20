import { Command } from "commander";
import { existsSync, readdirSync, lstatSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";
import { getAllAgents, getUniversalAgents, getNonUniversalAgents } from "../core/agent-detector.js";
import { info, dim } from "../utils/logger.js";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";
import * as p from "@clack/prompts";
import pc from "picocolors";

interface ListOptions {
  global?: boolean;
  project?: boolean;
  agent?: string[];
  all?: boolean;
}

export function registerList(program: Command) {
  program
    .command("list")
    .alias("ls")
    .description("List installed skills")
    .option("-g, --global", "List global skills only")
    .option("-p, --project", "List project skills only")
    .option("-a, --all", "List all skills (both global and project)")
    .option("--agent <agents...>", "Filter by specific agents")
    .action(async (opts: ListOptions) => {
      let scopeGlobal: boolean | null = null;

      if (opts.global) {
        scopeGlobal = true;
      } else if (opts.project) {
        scopeGlobal = false;
      } else {
        const scopeSelection = await p.select({
          message: "Which scope to list?",
          options: [
            { value: "all", label: "All (global + project)" },
            { value: "global", label: "Global only" },
            { value: "project", label: "Project only" },
          ],
        });

        if (p.isCancel(scopeSelection)) {
          console.log("Cancelled.");
          return;
        }

        if (scopeSelection === "global") {
          scopeGlobal = true;
        } else if (scopeSelection === "project") {
          scopeGlobal = false;
        }
      }

      const universalAgents = getUniversalAgents();
      const nonUniversalAgents = getNonUniversalAgents();

      const universalSection = {
        title: "Universal (.agents/skills)",
        items: universalAgents.map((a) => ({
          value: a.key,
          label: a.name,
        })),
      };

      const selectableItems = nonUniversalAgents.map((a) => ({
        value: a.key,
        label: a.name,
      }));

      const agentSelection = await searchMultiselect({
        message: "Which agents to list from?",
        items: selectableItems,
        lockedSection: universalSection,
      });

      if (agentSelection === cancelSymbol) {
        console.log("Cancelled.");
        return;
      }

      const selectedAgents = agentSelection as string[];
      const agents = getAllAgents().filter((a) => selectedAgents.includes(a.key));

      if (agents.length === 0) {
        console.log("No agents selected.");
        return;
      }

      console.log("");

      let found = false;
      for (const agent of agents) {
        const showProject = scopeGlobal === null || scopeGlobal === false;
        const showGlobal = scopeGlobal === null || scopeGlobal === true;

        if (showProject) {
          const projectDir = join(process.cwd(), agent.skillsDir);
          const skills = getSkillsInDir(projectDir);
          if (skills.length > 0) {
            found = true;
            info(`\n${agent.name} (project):`);
            for (const s of skills) {
              dim(`  ${s}`);
            }
          }
        }

        if (showGlobal && agent.globalSkillsDir) {
          const globalDir = join(homedir(), agent.globalSkillsDir);
          const skills = getSkillsInDir(globalDir);
          if (skills.length > 0) {
            found = true;
            info(`\n${agent.name} (global):`);
            for (const s of skills) {
              dim(`  ${s}`);
            }
          }
        }
      }

      if (!found) {
        dim("No skills installed for selected agents and scope.");
      }

      console.log("");
    });
}

function getSkillsInDir(dir: string): string[] {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).filter((f) => {
    const full = join(dir, f);
    try {
      const stat = lstatSync(full);
      return stat.isDirectory() && existsSync(join(full, "SKILL.md"));
    } catch {
      return false;
    }
  });
}
