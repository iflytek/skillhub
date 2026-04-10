import { Command } from "commander";
import { mkdtemp, rm, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createWriteStream, existsSync, mkdirSync } from "node:fs";
import { ApiClient } from "../core/api-client.js";
import { loadConfig } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { discoverSkills } from "../core/skill-discovery.js";
import { installSkill } from "../core/installer.js";
import { getAllAgents, detectInstalledAgents } from "../core/agent-detector.js";
import { parseSource, getCloneUrl } from "../core/source-parser.js";
import { addToLock } from "../core/skill-lock.js";
import { success, error, info, dim } from "../utils/logger.js";
import { multiSelect } from "../utils/prompts.js";
import ora from "ora";
import { execSync } from "node:child_process";

export type SourceType = "auto" | "registry" | "git" | "local";

function detectSourceType(arg: string): SourceType {
  if (arg.startsWith(".") || arg.startsWith("/") || arg.startsWith("~")) {
    return "local";
  }
  if (arg.includes("github.com") || arg.includes("gitlab.com") || arg.includes("://")) {
    return "git";
  }
  if (/^[\w-]+\/[\w-]+$/.test(arg)) {
    return "git";
  }
  return "registry";
}

function getInstallSpinner(sourceType: SourceType, arg: string): string {
  if (sourceType === "registry") {
    return `Fetching ${arg}`;
  }
  return `Resolving ${arg}`;
}

export function registerInstall(program: Command) {
  program
    .command("install <source>")
    .alias("i")
    .description("Install skills from registry, git repositories, or local paths")
    .option("--source <type>", "Source type: auto, registry, git, local (default: auto)")
    .option("--namespace <ns>", "Namespace for registry install (default: global)")
    .option("-s, --skill <skills...>", "Install specific skills by name (for git/local sources)")
    .option("-a, --agent <agents...>", "Target specific agents")
    .option("-g, --global", "Install to global scope")
    .option("-y, --yes", "Skip all prompts")
    .option("--copy", "Copy instead of symlink")
    .option("--list", "List available skills without installing")
    .action(async (source: string, opts: Record<string, string | string[] | boolean>) => {
      const sourceType = (opts.source as SourceType) || "auto";
      const effectiveSource = sourceType === "auto" ? detectSourceType(source) : sourceType;

      const spinner = ora(getInstallSpinner(effectiveSource, source)).start();

      try {
        if (effectiveSource === "registry") {
          await installFromRegistry(source, opts, spinner);
        } else {
          await installFromGit(source, effectiveSource, opts, spinner);
        }
      } catch (e: any) {
        spinner.fail(e.message);
        process.exit(1);
      }
    });
}

async function installFromRegistry(slug: string, opts: Record<string, string | string[] | boolean>, spinner: any) {
  const ns = (opts.namespace as string) || "global";
  const config = loadConfig();
  const token = await readToken();
  const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

  spinner.text = `Fetching ${ns}/${slug}`;

  const downloadUrl = `/api/v1/skills/${ns}/${slug}/download`;
  const tmpDir = await mkdtemp(join(tmpdir(), "skillhub-install-"));
  const zipPath = join(tmpDir, `${slug}.zip`);

  const { request } = await import("undici");
  const url = new URL(downloadUrl, config.registry);
  const { statusCode, body } = await request(url.toString(), {
    method: "GET",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (statusCode >= 400) {
    spinner.fail(`Skill not found: ${slug}`);
    process.exit(1);
  }

  spinner.text = "Downloading";
  const fileStream = createWriteStream(zipPath);
  await body.pipe(fileStream);

  spinner.text = "Extracting";
  const extractDir = join(tmpDir, "extracted");
  mkdirSync(extractDir, { recursive: true });
  execSync(`unzip -o "${zipPath}" -d "${extractDir}"`, { stdio: "pipe" });

  const skills = discoverSkills(extractDir);
  if (skills.length === 0) {
    spinner.fail("No SKILL.md found in package");
    process.exit(1);
  }

  spinner.succeed(`Found ${skills.length} skill(s) in ${slug}`);

  if (opts.list) {
    for (const s of skills) {
      info(`${s.name}`);
      dim(`  ${s.description}`);
    }
    return;
  }

  let selectedSkills = skills;
  if (!opts.yes && skills.length > 1) {
    const selected = await multiSelect(
      "Select skills to install (space to toggle, comma-separated numbers):",
      skills.map((s) => ({ value: s.name, label: `${s.name} — ${s.description}` }))
    );
    if (!selected) {
      console.log("Cancelled.");
      return;
    }
    selectedSkills = skills.filter((s) => selected.includes(s.name));
  }

  const targetAgents = opts.agent
    ? getAllAgents().filter((a) => (opts.agent as string[]).includes(a.key))
    : detectInstalledAgents();

  if (targetAgents.length === 0) {
    const claude = getAllAgents().find((a) => a.key === "claude-code");
    if (claude) targetAgents.push(claude);
  }

  const mode = opts.copy ? ("copy" as const) : ("symlink" as const);
  const isGlobal = !!opts.global;

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
        await addToLock(skill.name, {
          source: `${ns}/${slug}`,
          sourceType: "registry",
          sourceUrl: `${config.registry}/api/v1/skills/${ns}/${slug}`,
          namespace: ns,
          slug: skill.name,
          version: "latest",
        });
      }
    }
  }

  success(`Installed ${installed} skill(s) from ${slug}`);
  await rm(tmpDir, { recursive: true, force: true });
}

async function installFromGit(source: string, sourceType: SourceType, opts: Record<string, string | string[] | boolean>, spinner: any) {
  let skillsDir: string;

  const parsed = parseSource(source);

  if (parsed.skillFilter) {
    opts.skill = opts.skill || [];
    if (!Array.isArray(opts.skill)) {
      opts.skill = [opts.skill as string];
    }
    if (!opts.skill.includes(parsed.skillFilter)) {
      opts.skill.push(parsed.skillFilter);
    }
  }

  if (parsed.type === "local") {
    skillsDir = parsed.localPath!;
    spinner.text = "Scanning local directory";
  } else {
    const cloneUrl = getCloneUrl(parsed);
    spinner.text = `Cloning ${cloneUrl}`;
    const tmpDir = await mkdtemp(join(tmpdir(), "skillhub-install-"));
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
    for (const s of skills) {
      info(`${s.name}`);
      dim(`  ${s.description}`);
    }
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
    const selected = await multiSelect(
      "选择要安装的 skills (按空格分隔输入):",
      skills.map((s) => ({ value: s.name, label: `${s.name} — ${s.description}` }))
    );

    if (!selected) {
      console.log("已取消安装");
      return;
    }

    selectedSkills = skills.filter((s) => selected.includes(s.name));
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
        const sourceUrl = parsed.type === "local"
          ? (parsed.localPath as string)
          : getCloneUrl(parsed);
        await addToLock(skill.name, {
          source: source,
          sourceType: parsed.type === "local" ? "local" : "git",
          sourceUrl: sourceUrl,
          ref: parsed.ref,
          namespace: "global",
          slug: skill.name,
          version: parsed.ref || "main",
        });
      } else {
        error(`Failed to install ${skill.name} to ${agent.name}: ${result.error}`);
      }
    }
  }

  success(`Installed ${installed} skill(s) to ${targetAgents.length} agent(s)`);
}
