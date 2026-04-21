import { Command } from "commander";
import { mkdtemp, rm, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createWriteStream, createReadStream, existsSync, mkdirSync } from "node:fs";
import { ApiClient } from "../core/api-client.js";
import { loadConfigFromProgram } from "../core/config.js";
import { readToken } from "../core/auth-token.js";
import { discoverSkills } from "../core/skill-discovery.js";
import { installSkill } from "../core/installer.js";
import { getAllAgents, detectInstalledAgents, isUniversalForScope, getAgentTargetDir, type AgentInfo } from "../core/agent-detector.js";
import { parseSource, getCloneUrl } from "../core/source-parser.js";
import { addToLock } from "../core/skill-lock.js";
import { success, error, info, dim } from "../utils/logger.js";
import chalk from "chalk";
import unzipper from "unzipper";
import { multiSelect, sectionMultiSelect } from "../utils/prompts.js";
import { searchMultiselect, cancelSymbol } from "../utils/search-multiselect.js";
import { runInteractiveSearch, searchSkills } from "../core/interactive-search.js";
import type { SkillVersionItem } from "../schema/routes.js";

interface SkillTag {
  id: number;
  tagName: string;
  versionId: number;
  createdAt: string;
}
import * as p from "@clack/prompts";
import pc from "picocolors";
import ora from "ora";
import { execSync } from "node:child_process";
import { finished } from "node:stream/promises";

export type SourceType = "auto" | "registry" | "git" | "local";

function detectSourceType(arg: string): SourceType {
  if (arg.startsWith(".") || arg.startsWith("/") || arg.startsWith("~")) {
    return "local";
  }
  if (arg.includes("github.com") || arg.includes("gitlab.com") || arg.includes("://") || arg.endsWith(".git")) {
    return "git";
  }
  if (/^[\w-]+\/[\w-]+$/.test(arg)) {
    return "registry";
  }
  return "registry";
}

function getInstallSpinner(sourceType: SourceType, arg: string): string {
  if (sourceType === "registry") {
    return `Searching registry for ${arg}`;
  }
  return `Resolving ${arg}`;
}

interface InstallResult {
  skill: string;
  agent: string;
  success: boolean;
  path: string;
  error?: string;
}

/**
 * Build grouped install result lines: agents sharing the same install path
 * are merged into one line for cleaner output.
 *
 * Example:
 *   ✓ fork-workflow
 *     → Amp, Cline, Codex, Cursor, Deep Agents +7: .agents/skills/fork-workflow
 *     → Claude Code: .claude/skills/fork-workflow
 */
function buildInstallResultLines(
  selectedSkills: { name: string }[],
  results: InstallResult[],
): string[] {
  const MAX_NAMES = 5;
  const resultLines: string[] = [];

  for (const skill of selectedSkills) {
    const skillResults = results.filter((r) => r.skill === skill.name && r.success);
    if (skillResults.length === 0) continue;

    resultLines.push(`${pc.green("✓")} ${skill.name}`);

    // Group agents by install path
    const pathGroups = new Map<string, string[]>();
    for (const r of skillResults) {
      const agents = pathGroups.get(r.path) || [];
      agents.push(r.agent);
      pathGroups.set(r.path, agents);
    }

    // Sort groups: put the group with the most agents first
    const sortedGroups = [...pathGroups.entries()].sort((a, b) => b[1].length - a[1].length);

    for (const [path, agents] of sortedGroups) {
      const sorted = agents.sort((a, b) => a.localeCompare(b));
      let label: string;
      if (sorted.length <= MAX_NAMES) {
        label = sorted.join(", ");
      } else {
        const shown = sorted.slice(0, MAX_NAMES).join(", ");
        const extra = sorted.length - MAX_NAMES;
        label = `${shown} ${pc.dim(`+${extra}`)}`;
      }
      resultLines.push(`  ${pc.dim("→")} ${label}: ${pc.dim(path)}`);
    }
  }

  return resultLines;
}

async function selectAgentsInteractive(isGlobal: boolean): Promise<string[] | null> {
  const allAgents = getAllAgents();

  // Use scope-aware universal check: an agent is "universal" when its target
  // install dir equals the canonical .agents/skills directory for the given scope.
  // Exclude agents with showInUniversalList === false from the locked section.
  const universalAgents = allAgents
    .filter((a) => isUniversalForScope(a, isGlobal) && a.showInUniversalList !== false)
    .sort((a, b) => a.name.localeCompare(b.name));
  const nonUniversalAgents = allAgents
    .filter((a) => !isUniversalForScope(a, isGlobal))
    .sort((a, b) => a.name.localeCompare(b.name));

  const canonicalLabel = isGlobal ? "Universal (~/.agents/skills)" : "Universal (.agents/skills)";
  const lockedSection = {
    title: canonicalLabel,
    items: universalAgents.map((a) => ({
      value: a.key,
      label: a.name,
    })),
  };

  const selectableItems = nonUniversalAgents.map((a) => ({
    value: a.key,
    label: a.name,
    hint: isGlobal ? (a.globalSkillsDir || a.skillsDir) : a.skillsDir,
  }));

  const result = await searchMultiselect({
    message: "Which agents do you want to install to?",
    items: selectableItems,
    lockedSection,
  });

  if (result === cancelSymbol) {
    return null;
  }

  return result as string[];
}

async function selectInstallMode(): Promise<"symlink" | "copy" | null> {
  const result = await p.select({
    message: "Installation method?",
    options: [
      {
        value: "symlink",
        label: "Symlink (Recommended)",
        hint: "single source of truth",
      },
      {
        value: "copy",
        label: "Copy to all agents",
        hint: "independent copies",
      },
    ],
  });

  if (p.isCancel(result)) {
    return null;
  }

  return result as "symlink" | "copy";
}

function buildAgentSummary(targetAgents: AgentInfo[], mode: "symlink" | "copy", isGlobal: boolean): string[] {
  const lines: string[] = [];
  const universal = targetAgents.filter((a) => isUniversalForScope(a, isGlobal));
  const symlinked = targetAgents.filter((a) => !isUniversalForScope(a, isGlobal));

  const sortNames = (agents: AgentInfo[]) => agents.map((a) => a.name).sort((a, b) => a.localeCompare(b));

  if (mode === "symlink") {
    if (universal.length > 0) {
      lines.push(`  universal: ${sortNames(universal).join(", ")}`);
    }
    if (symlinked.length > 0) {
      lines.push(`  symlink → ${sortNames(symlinked).join(", ")}`);
    }
  } else {
    lines.push(`  copy → ${sortNames(targetAgents).join(", ")}`);
  }

  return lines;
}

function buildInstallHelp(cmd: Command): string {
  const lines: string[] = [];

  lines.push(`${chalk.bold("Usage:")} skillhub install|i [options] ${chalk.cyan("<skill-name>")}`);
  lines.push("");
  lines.push("Install skills from registry, git repositories, or local paths");
  lines.push("");

  lines.push(chalk.bold("Arguments:"));
  lines.push(`  ${chalk.cyan("skill-name")}                Skill name or namespace/skill-name from registry`);
  lines.push("");

  lines.push(chalk.bold("Source Options:"));
  lines.push(`  ${chalk.cyan("-a, --add <source>")}        Install from GitHub or local path (alias for --from)`);
  lines.push(`  ${chalk.cyan("--from <source>")}           Install from GitHub or local path (alias for -a)`);
  lines.push("");

  lines.push(chalk.bold("Target Options:"));
  lines.push(`  ${chalk.cyan("--agent <agents...>")}       Target specific agents`);
  lines.push(`  ${chalk.cyan("-g, --global")}              Install to global scope`);
  lines.push("");

  lines.push(chalk.bold("Version Options:"));
  lines.push(`  ${chalk.cyan("-v, --skill-version <ver>")}  Install specific version (non-interactive)`);
  lines.push(`  ${chalk.cyan("--tag <tag>")}               Install specific tag (non-interactive, resolves to version)`);
  lines.push("");

  lines.push(chalk.bold("Mode Options:"));
  lines.push(`  ${chalk.cyan("--copy")}                    Copy instead of symlink`);
  lines.push(`  ${chalk.cyan("--list")}                    List available skills without installing`);
  lines.push("");

  lines.push(chalk.bold("Other Options:"));
  lines.push(`  ${chalk.cyan("-y, --yes")}                 Skip all prompts`);
  lines.push(`  ${chalk.cyan("-h, --help")}                Display help for command`);
  lines.push("");

  lines.push(chalk.bold("Examples:"));
  lines.push(chalk.dim("  skillhub install vision2group/fork-workflow         Install a skill from registry"));
  lines.push(chalk.dim("  skillhub install my-skill --from ./local/path       Install from local directory"));
  lines.push(chalk.dim("  skillhub install my-skill --from github.com/user/repo Install from GitHub"));
  lines.push(chalk.dim("  skillhub install my-skill -g --yes                  Install globally, skip prompts"));
  lines.push(chalk.dim("  skillhub install my-skill --tag v1.0.0              Install specific tag"));

  return lines.join("\n");
}

export function registerInstall(program: Command) {
  const installCmd = program
    .command("install <skill-name>")
    .alias("i")
    .description("Install skills from registry, git repositories, or local paths")
    .option("-a, --add <source>", "Install from GitHub or local path (alias for --from)")
    .option("--from <source>", "Install from GitHub or local path (alias for -a)")
    .option("--agent <agents...>", "Target specific agents")
    .option("-g, --global", "Install to global scope")
    .option("-y, --yes", "Skip all prompts")
    .option("--copy", "Copy instead of symlink")
    .option("--list", "List available skills without installing")
    .option("-v, --skill-version <ver>", "Install specific version (non-interactive)")
    .option("--tag <tag>", "Install specific tag (non-interactive, resolves to version)")
    .configureHelp({ showGlobalOptions: true });

  const originalHelp = installCmd.helpInformation.bind(installCmd);
  installCmd.helpInformation = () => {
    return buildInstallHelp(installCmd);
  };

  installCmd.action(async (source: string, opts: Record<string, string | string[] | boolean>) => {
      const fromSource = (opts.from || opts.add) as string | undefined;

      let effectiveSource: SourceType;
      let installSource = source;

      if (fromSource) {
        effectiveSource = detectSourceType(fromSource);
        installSource = fromSource;
      } else {
        effectiveSource = detectSourceType(source);
      }

      const spinner = ora(getInstallSpinner(effectiveSource, installSource)).start();

      try {
        if (effectiveSource === "registry") {
          await installFromRegistry(source, opts, spinner, program);
        } else {
          await installFromGit(source, installSource, effectiveSource, opts, spinner, program);
        }
      } catch (e: any) {
        spinner.fail(e.message);
        process.exitCode = 1;
      }
    });
}

async function installFromRegistry(
  slug: string,
  opts: Record<string, string | string[] | boolean>,
  spinner: any,
  program: Command
) {
  let ns = "global";
  let actualSlug = slug;
  let userSpecifiedNamespace = false;

  if (slug.includes("/") && !slug.startsWith("/")) {
    const parts = slug.split("/");
    if (parts.length === 2) {
      ns = parts[0];
      actualSlug = parts[1];
      userSpecifiedNamespace = true;
    }
  }

  const config = loadConfigFromProgram(program);
  const token = await readToken();
  const client = new ApiClient({ baseUrl: config.registry, token: token || undefined });

  if (!userSpecifiedNamespace) {
    const results = await searchSkills(client, actualSlug, 50);

    // Deduplicate by namespace/name
    const seen = new Set<string>();
    const uniqueResults = results.filter(r => {
      const key = `${r.namespace}/${r.name}`;
      if (!seen.has(key)) {
        seen.add(key);
        return true;
      }
      return false;
    });

    if (uniqueResults.length === 0) {
      spinner.fail(`Skill not found: ${actualSlug}`);
      process.exitCode = 1;
    }

    if (uniqueResults.length === 1) {
      ns = uniqueResults[0].namespace;
      actualSlug = uniqueResults[0].name;
    } else {
      spinner.succeed(`Found ${actualSlug}`);
      const selected = await runInteractiveSearch(client, actualSlug);
      if (!selected) {
        console.log("Cancelled.");
        return;
      }
      spinner.start(`Fetching ${selected}`);
      const [selectedNs, selectedName] = selected.split("/", 2);
      ns = selectedNs;
      actualSlug = selectedName;
    }
  }

  spinner.text = `Fetching versions for ${ns}/${actualSlug}`;
  const [versionsResp, tagsResp] = await Promise.all([
    client.get<{ items: SkillVersionItem[] }>(`/api/v1/skills/${ns}/${actualSlug}/versions`),
    client.get<SkillTag[]>(`/api/v1/skills/${ns}/${actualSlug}/tags`).catch(() => [] as SkillTag[]),
  ]);

  const versions = versionsResp.items || [];

  // Map tags to versions by versionId
  const versionTagsMap = new Map<number, string[]>();
  for (const tag of tagsResp || []) {
    if (!versionTagsMap.has(tag.versionId)) {
      versionTagsMap.set(tag.versionId, []);
    }
    versionTagsMap.get(tag.versionId)!.push(tag.tagName);
  }

  // Present version selection
  let selectedVersion: string = "latest";
  if (opts.skillVersion) {
    selectedVersion = String(opts.skillVersion).replace(/^v/, "");
    const versionExists = versions.some((v) => v.version === selectedVersion);
    if (!versionExists) {
      spinner.fail(`Version not found: ${opts.skillVersion}`);
      if (versions.length > 0) {
        info(`Available versions: ${versions.map((v) => v.version).join(", ")}`);
      }
      process.exitCode = 1;
      return;
    }
  } else if (opts.tag) {
    for (const [vid, tags] of versionTagsMap) {
      if (tags.includes(opts.tag as string)) {
        const v = versions.find((ver) => ver.id === vid);
        if (v) {
          selectedVersion = v.version;
          break;
        }
      }
    }
    if (!selectedVersion) {
      // Fallback: use latest if tag not found
      selectedVersion = versions[0]?.version || "latest";
    }
  } else {
    // Interactive: show version selection
    spinner.succeed(`Found ${ns}/${actualSlug}`);

    const picked = await p.select({
      message: "Select version",
      options: versions.map((v) => ({
        value: v.version,
        label: `v${v.version}`,
        hint: versionTagsMap.get(v.id)?.join(", ") || "",
      })),
    });

    if (p.isCancel(picked)) {
      console.log("Cancelled.");
      return;
    }

    selectedVersion = picked as string;
    spinner.start(`Downloading ${ns}/${actualSlug}@${selectedVersion}`);
  }

  const baseUrl = config.registry.replace(/\/$/, "");
  const downloadUrl = `${baseUrl}/api/v1/skills/${ns}/${actualSlug}/versions/${selectedVersion}/download`;
  const tmpDir = await mkdtemp(join(tmpdir(), "skillhub-install-"));
  const zipPath = join(tmpDir, `${actualSlug}.zip`);

  spinner.text = `Downloading ${ns}/${actualSlug}@${selectedVersion}`;

  const { request } = await import("undici");
  let response = await request(downloadUrl, {
    method: "GET",
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  // undici 不自动跟随 redirect，手动处理 302/307/308
  if (response.statusCode === 302 || response.statusCode === 307 || response.statusCode === 308) {
    const location = response.headers.location;
    if (!location) {
      spinner.fail(`Redirect response has no Location header`);
      await rm(tmpDir, { recursive: true, force: true });
      process.exitCode = 1;
    }
          response = await request(location as string, { method: "GET" });
  }
  const { statusCode, body } = response;

  if (statusCode >= 400) {
    spinner.fail(`Skill not found: ${ns}/${actualSlug}`);
    await rm(tmpDir, { recursive: true, force: true });
    process.exitCode = 1;
    return;
  }

  const fileStream = createWriteStream(zipPath);
  await finished(body.pipe(fileStream));

  spinner.text = `Extracting ${actualSlug}`;
  const extractDir = join(tmpDir, "extracted");
  mkdirSync(extractDir, { recursive: true });
  await createReadStream(zipPath)
    .pipe(unzipper.Extract({ path: extractDir }))
    .promise();

  const skills = discoverSkills(extractDir);
  if (skills.length === 0) {
    spinner.fail("No SKILL.md found in package");
    process.exitCode = 1;
  }

  spinner.succeed(`Found ${skills.length} skill(s) in ${ns}/${actualSlug}`);

  if (opts.list) {
    const sorted = [...skills].sort((a, b) => a.name.localeCompare(b.name));
    for (const s of sorted) {
      info(`${s.name}`);
      dim(`  ${s.description}`);
    }
    return;
  }

  let selectedSkills = skills;
  if (!opts.yes && skills.length > 1) {
    const selected = await searchMultiselect({
      message: "Select skills to install",
      items: skills.map((s) => ({ value: s.name, label: s.name, hint: s.description })),
    });
    if (selected === cancelSymbol) {
      console.log("Cancelled.");
      return;
    }
    selectedSkills = skills.filter((s) => (selected as string[]).includes(s.name));
  }

  let isGlobal = !!opts.global;

  // Determine scope first, so that agent selection can use the correct
  // universal/non-universal grouping based on the actual scope.
  const allAgents = getAllAgents();
  const supportsGlobal = allAgents.some((a) => a.globalSkillsDir);

  if (opts.global === undefined && !opts.yes && supportsGlobal) {
    const scope = await p.select({
      message: "Installation scope",
      options: [
        {
          value: false,
          label: "Project",
          hint: "Install in current directory (committed with your project)",
        },
        {
          value: true,
          label: "Global",
          hint: "Install in home directory (available across all projects)",
        },
      ],
    });

    if (p.isCancel(scope)) {
      console.log("Cancelled.");
      return;
    }

    isGlobal = scope as boolean;
  }

  let targetAgents = opts.agent
    ? allAgents.filter((a) => (opts.agent as string[]).includes(a.key))
    : detectInstalledAgents();

  if (targetAgents.length === 0) {
    const claude = allAgents.find((a) => a.key === "claude-code");
    if (claude) targetAgents.push(claude);
  }

  let mode: "symlink" | "copy" = opts.copy ? "copy" : "symlink";

  if (!opts.yes && !opts.agent) {
    const selected = await selectAgentsInteractive(isGlobal);
    if (!selected) {
      console.log("Cancelled.");
      return;
    }
    targetAgents = allAgents.filter((a) => selected.includes(a.key));
  }

  // Only prompt for install mode when there are multiple unique target directories.
  // When all selected agents share the same skillsDir, symlink vs copy is meaningless.
  const uniqueDirs = new Set(targetAgents.map((a) => getAgentTargetDir(a, isGlobal)));

  if (uniqueDirs.size <= 1) {
    // Single target directory — default to copy (no symlink needed)
    mode = 'copy';
  } else if (!opts.yes) {
    const selectedMode = await selectInstallMode();
    if (selectedMode === null) {
      console.log("Cancelled.");
      return;
    }
    mode = selectedMode;
  }

  const cwd = process.cwd();
  const summaryLines: string[] = [];

  for (const skill of selectedSkills) {
    if (summaryLines.length > 0) summaryLines.push("");
    const canonicalPath = isGlobal
      ? `~/.agents/skills/${skill.name}`
      : `./.agents/skills/${skill.name}`;
    summaryLines.push(`${pc.cyan(canonicalPath)}`);
    for (const line of buildAgentSummary(targetAgents, mode, isGlobal)) {
      summaryLines.push(`  ${line}`);
    }
  }
  summaryLines.push("");
  summaryLines.push(`${pc.dim("Mode:")} ${mode}`);
  summaryLines.push(`${pc.dim("Scope:")} ${isGlobal ? "global" : "project"}`);

  console.log("");
  p.note(summaryLines.join("\n"), "Installation Summary");

  if (!opts.yes) {
    const confirmed = await p.confirm({ message: "Proceed with installation?" });

    if (p.isCancel(confirmed) || !confirmed) {
      console.log("Cancelled.");
      return;
    }
  }

  spinner.start("Installing skills...");

  let installed = 0;
  let failed = 0;
  const results: { skill: string; agent: string; success: boolean; path: string; error?: string }[] = [];

  for (const skill of selectedSkills) {
    for (const agent of targetAgents) {
      const result = installSkill(
        skill.dir,
        skill.name,
        agent.key,
        getAgentTargetDir(agent, isGlobal),
        mode,
        isGlobal,
        agent,
      );
      results.push({
        skill: skill.name,
        agent: agent.name,
        success: result.success,
        path: result.path || "",
        error: result.error,
      });
      if (result.success) {
        installed++;
        await addToLock(skill.name, {
          source: `${ns}/${actualSlug}`,
          sourceType: "registry",
          sourceUrl: `${config.registry}/api/v1/skills/${ns}/${actualSlug}`,
          namespace: ns,
          slug: skill.name,
          version: "latest",
        });
      } else {
        failed++;
        error(`Failed to install ${skill.name} to ${agent.name}: ${result.error}`);
      }
    }
  }

  spinner.succeed("Installation complete");

  console.log("");
  const successful = results.filter((r) => r.success);

  if (successful.length > 0) {
    const resultLines = buildInstallResultLines(selectedSkills, results);
    p.note(resultLines.join("\n"), `Installed ${successful.length} skill(s)`);
  }

  if (failed > 0) {
    p.log.error(pc.red(`Failed to install ${failed}`));
    for (const r of results.filter((r) => !r.success)) {
      p.log.message(`${pc.red("✗")} ${r.skill} → ${r.agent}: ${pc.dim(r.error || "unknown error")}`);
    }
  }

  console.log("");
  p.outro(pc.green("Done!") + pc.dim("  Review skills before use; they run with full agent permissions."));

  await rm(tmpDir, { recursive: true, force: true });
}

async function installFromGit(
  skillName: string,
  source: string,
  sourceType: SourceType,
  opts: Record<string, string | string[] | boolean>,
  spinner: any,
  program: Command
) {
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

  // If skillName is a skill identifier (not a path), use it to filter
  if (skillName && !skillName.startsWith(".") && !skillName.startsWith("/") && !skillName.startsWith("~")) {
    opts.skill = opts.skill || [];
    if (!Array.isArray(opts.skill)) {
      opts.skill = [opts.skill as string];
    }
    if (!opts.skill.includes(skillName)) {
      opts.skill.push(skillName);
    }
  }

  if (parsed.type === "local") {
    skillsDir = parsed.localPath!;
    spinner.text = `Scanning ${parsed.localPath}`;
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
    process.exitCode = 1;
  }

  spinner.succeed(`Found ${skills.length} skill(s)`);

  if (opts.list) {
    const sorted = [...skills].sort((a, b) => a.name.localeCompare(b.name));
    for (const s of sorted) {
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
        process.exitCode = 1;
      }
    }
  } else if (!opts.yes && skills.length > 1) {
    const selected = await searchMultiselect({
      message: "Select skills to install",
      items: skills.map((s) => ({ value: s.name, label: s.name, hint: s.description })),
    });

    if (selected === cancelSymbol) {
      console.log("Cancelled.");
      return;
    }

    selectedSkills = skills.filter((s) => (selected as string[]).includes(s.name));
  }

  let isGlobal = !!opts.global;

  // Determine scope first, so that agent selection can use the correct
  // universal/non-universal grouping based on the actual scope.
  const allAgents = getAllAgents();
  const supportsGlobal = allAgents.some((a) => a.globalSkillsDir);

  if (opts.global === undefined && !opts.yes && supportsGlobal) {
    const scope = await p.select({
      message: "Installation scope",
      options: [
        {
          value: false,
          label: "Project",
          hint: "Install in current directory (committed with your project)",
        },
        {
          value: true,
          label: "Global",
          hint: "Install in home directory (available across all projects)",
        },
      ],
    });

    if (p.isCancel(scope)) {
      console.log("Cancelled.");
      return;
    }

    isGlobal = scope as boolean;
  }

  let targetAgents = opts.agent
    ? allAgents.filter((a) => (opts.agent as string[]).includes(a.key))
    : detectInstalledAgents();

  if (targetAgents.length === 0) {
    if (!opts.yes) {
      info("No agents detected. Installing to Claude Code by default.");
    }
    const claude = allAgents.find((a) => a.key === "claude-code");
    if (claude) targetAgents.push(claude);
    else targetAgents.push(allAgents[0]);
  }

  let mode: "symlink" | "copy" = opts.copy ? "copy" : "symlink";

  if (!opts.yes && !opts.agent) {
    const selected = await selectAgentsInteractive(isGlobal);
    if (!selected) {
      console.log("Cancelled.");
      return;
    }
    targetAgents = allAgents.filter((a) => selected.includes(a.key));
  }

  // Only prompt for install mode when there are multiple unique target directories.
  // When all selected agents share the same skillsDir, symlink vs copy is meaningless.
  const uniqueDirs = new Set(targetAgents.map((a) => getAgentTargetDir(a, isGlobal)));

  if (uniqueDirs.size <= 1) {
    // Single target directory — default to copy (no symlink needed)
    mode = 'copy';
  } else if (!opts.yes) {
    const selectedMode = await selectInstallMode();
    if (selectedMode === null) {
      console.log("Cancelled.");
      return;
    }
    mode = selectedMode;
  }

  const cwd = process.cwd();
  const summaryLines: string[] = [];

  for (const skill of selectedSkills) {
    if (summaryLines.length > 0) summaryLines.push("");
    const canonicalPath = isGlobal
      ? `~/.agents/skills/${skill.name}`
      : `./.agents/skills/${skill.name}`;
    summaryLines.push(`${pc.cyan(canonicalPath)}`);
    for (const line of buildAgentSummary(targetAgents, mode, isGlobal)) {
      summaryLines.push(`  ${line}`);
    }
  }
  summaryLines.push("");
  summaryLines.push(`${pc.dim("Mode:")} ${mode}`);
  summaryLines.push(`${pc.dim("Scope:")} ${isGlobal ? "global" : "project"}`);

  console.log("");
  p.note(summaryLines.join("\n"), "Installation Summary");

  if (!opts.yes) {
    const confirmed = await p.confirm({ message: "Proceed with installation?" });

    if (p.isCancel(confirmed) || !confirmed) {
      console.log("Cancelled.");
      return;
    }
  }

  spinner.start("Installing skills...");

  let installed = 0;
  let failed = 0;
  const results: { skill: string; agent: string; success: boolean; path: string; error?: string }[] = [];

  for (const skill of selectedSkills) {
    for (const agent of targetAgents) {
      const result = installSkill(
        skill.dir,
        skill.name,
        agent.key,
        getAgentTargetDir(agent, isGlobal),
        mode,
        isGlobal,
        agent,
      );
      results.push({
        skill: skill.name,
        agent: agent.name,
        success: result.success,
        path: result.path || "",
        error: result.error,
      });
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
        failed++;
        error(`Failed to install ${skill.name} to ${agent.name}: ${result.error}`);
      }
    }
  }

  spinner.succeed("Installation complete");

  console.log("");
  const successful = results.filter((r) => r.success);

  if (successful.length > 0) {
    const resultLines = buildInstallResultLines(selectedSkills, results);
    p.note(resultLines.join("\n"), `Installed ${successful.length} skill(s)`);
  }

  if (failed > 0) {
    p.log.error(pc.red(`Failed to install ${failed}`));
    for (const r of results.filter((r) => !r.success)) {
      p.log.message(`${pc.red("✗")} ${r.skill} → ${r.agent}: ${pc.dim(r.error || "unknown error")}`);
    }
  }

  console.log("");
  p.outro(pc.green("Done!") + pc.dim("  Review skills before use; they run with full agent permissions."));
}
