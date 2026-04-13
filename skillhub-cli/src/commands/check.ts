import { Command } from "commander";
import { existsSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";
import { getAllAgents } from "../core/agent-detector.js";
import { getAllLockedSkills, getSkillLockPath } from "../core/skill-lock.js";
import { success, error, info, warn, dim } from "../utils/logger.js";

interface CheckResult {
  name: string;
  status: "ok" | "missing" | "orphaned";
  source?: string;
  location?: string;
}

function findInstalledSkills(scope: "local" | "global"): Map<string, string[]> {
  const skillsMap = new Map<string, string[]>();
  const agents = getAllAgents();

  for (const agent of agents) {
    const baseDir = scope === "global"
      ? join(homedir(), agent.globalSkillsDir || agent.skillsDir)
      : join(process.cwd(), agent.skillsDir);

    if (!existsSync(baseDir)) continue;

    try {
      for (const entry of readdirSync(baseDir)) {
        const skillPath = join(baseDir, entry);
        if (statSync(skillPath).isDirectory() && existsSync(join(skillPath, "SKILL.md"))) {
          const existing = skillsMap.get(entry) || [];
          existing.push(agent.name);
          skillsMap.set(entry, existing);
        }
      }
    } catch {}
  }

  return skillsMap;
}

export function registerCheck(program: Command) {
  program
    .command("check")
    .description("Check installed skills against lock file")
    .option("--global", "Check global scope skills")
    .option("--json", "Output results as JSON")
    .action(async (opts: { global?: boolean; json?: boolean }) => {
      const scope = opts.global ? "global" : "local";
      const lockPath = getSkillLockPath();

      if (!existsSync(lockPath)) {
        if (opts.json) {
          console.log(JSON.stringify({ error: "No lock file found" }, null, 2));
        } else {
          warn("No skillhub.lock found. Have you installed any skills?");
        }
        return;
      }

      const lockedSkills = await getAllLockedSkills();
      const installedSkills = findInstalledSkills(scope);

      const results: CheckResult[] = [];

      for (const [name, entry] of Object.entries(lockedSkills)) {
        const installedLocations = installedSkills.get(name);
        if (installedLocations && installedLocations.length > 0) {
          results.push({
            name,
            status: "ok",
            source: entry.source,
            location: installedLocations.join(", "),
          });
        } else {
          results.push({
            name,
            status: "missing",
            source: entry.source,
          });
        }
      }

      for (const [name, locations] of installedSkills.entries()) {
        if (!lockedSkills[name]) {
          results.push({
            name,
            status: "orphaned",
            location: locations.join(", "),
          });
        }
      }

      if (opts.json) {
        console.log(JSON.stringify(results, null, 2));
        return;
      }

      console.log("");
      info(`SkillHub Lock Check (${scope} scope):`);
      console.log("");

      if (results.length === 0) {
        dim("  No skills found.");
        console.log("");
        return;
      }

      let ok = 0, missing = 0, orphaned = 0;

      for (const r of results) {
        if (r.status === "ok") {
          ok++;
          success(`  ✓ ${r.name}`);
          dim(`    Source: ${r.source}`);
          dim(`    Location: ${r.location}`);
        } else if (r.status === "missing") {
          missing++;
          error(`  ✗ ${r.name}`);
          dim(`    Source: ${r.source}`);
          dim(`    Status: NOT INSTALLED`);
        } else if (r.status === "orphaned") {
          orphaned++;
          warn(`  ! ${r.name}`);
          dim(`    Location: ${r.location}`);
          dim(`    Status: NOT IN LOCK FILE`);
        }
      }

      console.log("");
      dim(`Lock file: ${lockPath}`);
      dim(`Summary: ${ok} OK, ${missing} missing, ${orphaned} orphaned`);
      console.log("");
    });
}
