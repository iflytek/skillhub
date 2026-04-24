#!/usr/bin/env node
import { Command } from "commander";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// ANSI helpers (zero-dependency)
const bold = (s: string) => `\x1b[1m${s}\x1b[0m`;
const dim = (s: string) => `\x1b[2m${s}\x1b[0m`;
const cyan = (s: string) => `\x1b[36m${s}\x1b[0m`;

function getPackageVersion(): string {
  try {
    const pkgPath = resolve(__dirname, "../package.json");
    const pkg = JSON.parse(readFileSync(pkgPath, "utf-8"));
    return pkg.version;
  } catch {
    return "0.1.0";
  }
}

interface HelpEntry {
  cmd: string;
  desc: string;
  alias?: string;
}

function formatSection(header: string, entries: HelpEntry[]): string {
  const lines = [bold(header)];

  for (const e of entries) {
    const aliasPart = e.alias ? dim(` (${e.alias})`) : "";
    lines.push(`  ${cyan(e.cmd)}${aliasPart}`);
    lines.push(`    ${e.desc}`);
  }
  return lines.join("\n");
}

function buildTopLevelHelp(version: string): string {
  const sections: string[] = [];

  sections.push(`${bold("skillhub")} ${dim(`v${version}`)}`);
  sections.push(dim("CLI for SkillHub — publish, search, and manage agent skills"));
  sections.push("");

  sections.push(formatSection("Discovery", [
    { cmd: "explore", desc: "Browse or search skills from the registry", alias: "find, find-skills, search" },
    { cmd: "inspect <skill>", desc: "View skill metadata and versions", alias: "info, view" },
  ]));
  sections.push("");

  sections.push(formatSection("Install & Manage", [
    { cmd: "install <skill>", desc: "Install from registry, git, or local path", alias: "i" },
    { cmd: "download <skill>", desc: "Download a skill package to local directory" },
    { cmd: "update [skill]", desc: "Update installed skills from their source", alias: "up" },
    { cmd: "uninstall [skill]", desc: "Uninstall a skill from local agent", alias: "un" },
    { cmd: "list", desc: "List installed skills", alias: "ls" },
  ]));
  sections.push("");

  sections.push(formatSection("My Profile", [
    { cmd: "me skills", desc: "List your published skills", alias: "me ls" },
    { cmd: "me stars", desc: "List your starred skills" },
    { cmd: "me namespaces", desc: "List namespaces you have access to" },
    { cmd: "me submissions", desc: "List your review submissions" },
    { cmd: "rating <skill>", desc: "View your rating for a skill" },
  ]));
  sections.push("");

  sections.push(formatSection("Publish & Manage", [
    { cmd: "publish [path]", desc: "Publish a skill to SkillHub registry" },
    { cmd: "sync [path]", desc: "Scan and publish all skills from a directory" },
    { cmd: "delete <skill>", desc: "Delete a skill you own", alias: "del, unpublish" },
    { cmd: "archive <skill>", desc: "Archive a skill you own" },
    { cmd: "hide <skill>", desc: "Hide a skill" },
    { cmd: "unhide <skill>", desc: "Unhide a skill" },
  ]));
  sections.push("");

  sections.push(formatSection("Community", [
    { cmd: "star <skill>", desc: "Star or unstar a skill" },
    { cmd: "rate <skill> <score>", desc: "Rate a skill (1-5)" },
    { cmd: "report <skill>", desc: "Report a skill for review" },
  ]));
  sections.push("");

  sections.push(formatSection("Notifications & Admin", [
    { cmd: "notifications", desc: "Manage notifications", alias: "notif" },
    { cmd: "transfer <ns> <user>", desc: "Transfer namespace ownership" },
  ]));
  sections.push("");

  sections.push(formatSection("Configuration", [
    { cmd: "config list", desc: "Show current registry configuration" },
    { cmd: "config set <value>", desc: "Set registry URL" },
    { cmd: "config get", desc: "Get current registry configuration" },
    { cmd: "login", desc: "Authenticate with SkillHub registry" },
    { cmd: "logout", desc: "Remove stored authentication token" },
    { cmd: "whoami", desc: "Show current authenticated user" },
  ]));
  sections.push("");

  sections.push(bold("Examples"));
  sections.push(dim("  skillhub install vision2group/fork-workflow       Install a skill from registry"));
  sections.push(dim("  skillhub install find-skills --from https://...   Install from GitHub or local path"));
  sections.push(dim("  skillhub explore                                  Interactive skill search"));
  sections.push(dim("  skillhub explore --hot                            Browse popular skills"));
  sections.push(dim("  skillhub config list                              Show current configuration"));
  sections.push(dim("  skillhub --registry <url> explore                 One-time registry override"));
  sections.push(dim("  skillhub publish                                  Publish current directory"));
  sections.push(dim("  skillhub me skills                                List your published skills"));
  sections.push(dim("  skillhub update                                   Update installed skills"));
  sections.push("");
  sections.push(dim("Run 'skillhub <command> --help' for command-specific options."));
  sections.push("");

  sections.push(bold("Global Options"));
  sections.push(`  ${cyan("--registry <url>")}   Registry API base URL`);
  sections.push(`  ${cyan("--json")}              Output results as JSON`);
  sections.push(`  ${cyan("--help")}              Show help for a command`);
  sections.push(`  ${cyan("--version")}           Show version number`);

  return sections.join("\n");
}

export async function createCli(): Promise<Command> {
  const program = new Command();
  const version = getPackageVersion();

  program
    .name("skillhub")
    .description("CLI for SkillHub — publish, search, and manage agent skills")
    .version(version)
    .option("--registry <url>", "Registry API base URL")
    .option("--json", "Output results as JSON")
    .option("--debug", "Show debug information for API requests");

  const customHelp = buildTopLevelHelp(version);
  const originalHelpInformation = program.helpInformation.bind(program);
  program.helpInformation = () => {
    if (program.parent) return originalHelpInformation();
    return customHelp;
  };

  const [
    { registerLogin },
    { registerLogout },
    { registerWhoami },
    { registerPublish },
    { registerInstall },
    { registerDownload },
    { registerList },
    { registerStar },
    { registerInit },
    { registerMe },
    { registerReviews },
    { registerNotifications },
    { registerDelete },
    { registerReport },
    { registerResolve },
    { registerRating, registerRate },
    { registerArchive },
    { registerUpdate },
    { registerCheck },
    { registerUninstall },
    { registerSync },
    { registerInspect },
    { registerExplore },
    { registerTransfer },
    { registerHide, registerUnhide },
    { registerConfig },
  ] = await Promise.all([
    import("./commands/login.js"),
    import("./commands/logout.js"),
    import("./commands/whoami.js"),
    import("./commands/publish.js"),
    import("./commands/install.js"),
    import("./commands/download.js"),
    import("./commands/list.js"),
    import("./commands/star.js"),
    import("./commands/init.js"),
    import("./commands/me.js"),
    import("./commands/reviews.js"),
    import("./commands/notifications.js"),
    import("./commands/delete.js"),
    import("./commands/report.js"),
    import("./commands/resolve.js"),
    import("./commands/rating.js"),
    import("./commands/archive.js"),
    import("./commands/update.js"),
    import("./commands/check.js"),
    import("./commands/uninstall.js"),
    import("./commands/sync.js"),
    import("./commands/inspect.js"),
    import("./commands/explore.js"),
    import("./commands/transfer.js"),
    import("./commands/hide.js"),
    import("./commands/config.js"),
  ]);

  registerLogin(program);
  registerLogout(program);
  registerWhoami(program);
  registerPublish(program);
  registerInstall(program);
  registerDownload(program);
  registerList(program);
  registerStar(program);
  registerInit(program);
  registerMe(program);
  registerReviews(program);
  registerNotifications(program);
  registerDelete(program);
  registerReport(program);
  registerResolve(program);
  registerRating(program);
  registerRate(program);
  registerArchive(program);
  registerUpdate(program);
  registerCheck(program);
  registerUninstall(program);
  registerSync(program);
  registerInspect(program);
  registerExplore(program);
  registerTransfer(program);
  registerHide(program);
  registerUnhide(program);
  registerConfig(program);

  return program;
}

export async function main() {
  const program = await createCli();
  program.parse();
}

main();
