import { Command } from "commander";
import { parseSource, getCloneUrl } from "../core/source-parser.js";
import { discoverSkills } from "../core/skill-discovery.js";
import { installSkill } from "../core/installer.js";
import { getAllAgents, detectInstalledAgents } from "../core/agent-detector.js";
import { success, error, info, dim } from "../utils/logger.js";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import ora from "ora";
import { execSync } from "node:child_process";

export function registerAdd(program: Command) {
  program
    .command("add <source>")
    .description("Install skills from git repositories or local paths")
    .option("-s, --skill <skills...>", "Install specific skills by name")
    .option("-a, --agent <agents...>", "Target specific agents")
    .option("-g, --global", "Install to global scope")
    .option("-y, --yes", "Skip all prompts")
    .option("--copy", "Copy files instead of symlinking")
    .option("--list", "List available skills without installing")
    .action(async (source: string, opts: Record<string, string | string[] | boolean>) => {
      const spinner = ora("Resolving source").start();

      try {
        const parsed = parseSource(source);
        let skillsDir: string;

        if (parsed.type === "local") {
          skillsDir = parsed.localPath!;
          spinner.text = "Scanning local directory";
        } else {
          const cloneUrl = getCloneUrl(parsed);
          spinner.text = `Cloning ${cloneUrl}`;
          const tmpDir = await mkdtemp(join(tmpdir(), "skillhub-add-"));
          const refArg = parsed.ref ? `--branch ${parsed.ref}` : "";
          const depth = parsed.ref ? "" : "--depth 1";
          execSync(`git clone ${depth} ${refArg} ${cloneUrl} ${tmpDir}`, { stdio: "pipe" });
          skillsDir = tmpDir;

          process.on("exit", () => { rm(tmpDir, { recursive: true, force: true }).catch(() => {}); });
        }

        spinner.text = "Discovering skills";
        const skills = discoverSkills(skillsDir);

        if (skills.length === 0) {
          spinner.fail("No skills found. Ensure the directory contains SKILL.md files.");
          process.exit(1);
        }

        spinner.succeed(`Found ${skills.length} skill(s)`);

        if (opts.list) {
          console.log("");
          for (const s of skills) {
            info(`${s.name}`);
            dim(`  ${s.description}`);
          }
          console.log("");
          return;
        }

        let selectedSkills = skills;
        if (opts.skill) {
          const skillNames = opts.skill as string[];
          if (skillNames.includes("*")) {
            selectedSkills = skills;
          } else {
            selectedSkills = skills.filter((s) => skillNames.includes(s.name));
            if (selectedSkills.length === 0) {
              error(`No matching skills for: ${skillNames.join(", ")}`);
              info("Available: " + skills.map((s) => s.name).join(", "));
              process.exit(1);
            }
          }
        } else if (!opts.yes && skills.length > 1) {
          console.log("");
          info("Available skills:");
          for (const s of skills) {
            console.log(`  ${s.name} — ${s.description}`);
          }
          console.log("");
        }

        const targetAgents = opts.agent
          ? getAllAgents().filter((a) => (opts.agent as string[]).includes(a.key))
          : detectInstalledAgents();

        if (targetAgents.length === 0) {
          const all = getAllAgents();
          if (!opts.yes) {
            info("No agents detected. Installing to Claude Code by default.");
          }
          const claude = all.find((a) => a.key === "claude-code");
          if (claude) targetAgents.push(claude);
          else targetAgents.push(all[0]);
        }

        const mode = opts.copy ? ("copy" as const) : ("symlink" as const);
        const isGlobal = !!opts.global;

        if (!opts.yes) {
          console.log("");
          info("Installation summary:");
          console.log(`  Skills:  ${selectedSkills.map((s) => s.name).join(", ")}`);
          console.log(`  Agents:  ${targetAgents.map((a) => a.name).join(", ")}`);
          console.log(`  Mode:    ${mode}`);
          console.log(`  Scope:   ${isGlobal ? "global" : "project"}`);
          console.log("");
        }

        let installed = 0;
        for (const skill of selectedSkills) {
          for (const agent of targetAgents) {
            const result = installSkill(
              skill.dir,
              skill.name,
              agent.key,
              isGlobal ? agent.globalPath : agent.projectPath,
              mode,
              isGlobal,
            );
            if (result.success) {
              installed++;
            } else {
              error(`Failed to install ${skill.name} to ${agent.name}: ${result.error}`);
            }
          }
        }

        success(`Installed ${installed} skill(s) to ${targetAgents.length} agent(s)`);
      } catch (e: any) {
        spinner.fail(e.message);
        process.exit(1);
      }
    });
}
