import { Command } from "commander";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { dirname } from "node:path";

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

function getPackageVersion(): string {
  try {
    const pkgPath = resolve(__dirname, "../package.json");
    const pkg = JSON.parse(readFileSync(pkgPath, "utf-8"));
    return pkg.version;
  } catch {
    return "0.1.0";
  }
}

export async function createCli(): Promise<Command> {
  const program = new Command();
  const version = getPackageVersion();

  program
    .name("skillhub")
    .description("CLI for SkillHub — publish, search, and manage agent skills")
    .version(version)
    .option("--registry <url>", "Registry API base URL", "http://localhost:8080")
    .option("--json", "Output results as JSON");

  const [
    { registerLogin },
    { registerLogout },
    { registerWhoami },
    { registerPublish },
    { registerNamespaces },
    { registerInstall },
    { registerDownload },
    { registerList },
    { registerStar },
    { registerInit },
    { registerMe },
    { registerReviews },
    { registerNotifications },
    { registerDelete },
    { registerVersions },
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
    { registerHide },
  ] = await Promise.all([
    import("./commands/login.js"),
    import("./commands/logout.js"),
    import("./commands/whoami.js"),
    import("./commands/publish.js"),
    import("./commands/namespaces.js"),
    import("./commands/install.js"),
    import("./commands/download.js"),
    import("./commands/list.js"),
    import("./commands/star.js"),
    import("./commands/init.js"),
    import("./commands/me.js"),
    import("./commands/reviews.js"),
    import("./commands/notifications.js"),
    import("./commands/delete.js"),
    import("./commands/versions.js"),
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
  ]);

  registerLogin(program);
  registerLogout(program);
  registerWhoami(program);
  registerPublish(program);
  registerNamespaces(program);
  registerInstall(program);
  registerDownload(program);
  registerList(program);
  registerStar(program);
  registerInit(program);
  registerMe(program);
  registerReviews(program);
  registerNotifications(program);
  registerDelete(program);
  registerVersions(program);
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

  return program;
}

export async function main() {
  const program = await createCli();
  program.parse();
}

main();
