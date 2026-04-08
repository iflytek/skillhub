import { describe, it, expect } from "vitest";
import { Command } from "commander";
import { registerInspect } from "../src/commands/inspect.js";
import { registerWhoami } from "../src/commands/whoami.js";
import { registerLogin } from "../src/commands/login.js";
import { registerPublish } from "../src/commands/publish.js";
import { registerMe } from "../src/commands/me.js";
import { registerVersions } from "../src/commands/versions.js";
import { registerNotifications } from "../src/commands/notifications.js";
import { registerReviews } from "../src/commands/reviews.js";
import { registerNamespaces } from "../src/commands/namespaces.js";
import { registerResolve } from "../src/commands/resolve.js";
import { registerRating, registerRate } from "../src/commands/rating.js";
import { registerStar } from "../src/commands/star.js";
import { registerDelete } from "../src/commands/delete.js";
import { registerArchive } from "../src/commands/archive.js";
import { registerReport } from "../src/commands/report.js";
import { registerSearch } from "../src/commands/search.js";
import { registerInstall } from "../src/commands/install.js";
import { registerDownload } from "../src/commands/download.js";
import { registerAdd } from "../src/commands/add.js";
import { registerInit } from "../src/commands/init.js";
import { registerList } from "../src/commands/list.js";
import { registerRemove } from "../src/commands/remove.js";
import { registerLogout } from "../src/commands/logout.js";
import { registerUninstall } from "../src/commands/uninstall.js";

describe("Command registrations", () => {
  function getCommandNames(program: Command): string[] {
    return program.commands.map((c) => c.name());
  }

  it("registers inspect command with info and view aliases", () => {
    const program = new Command();
    registerInspect(program);
    const names = getCommandNames(program);
    expect(names).toContain("inspect");
    const inspectCmd = program.commands.find((c) => c.name() === "inspect");
    expect(inspectCmd).toBeDefined();
    expect(inspectCmd!.aliases()).toContain("info");
    expect(inspectCmd!.aliases()).toContain("view");
  });

  it("registers whoami command", () => {
    const program = new Command();
    registerWhoami(program);
    expect(getCommandNames(program)).toContain("whoami");
  });

  it("registers login command", () => {
    const program = new Command();
    registerLogin(program);
    expect(getCommandNames(program)).toContain("login");
  });

  it("registers publish command with correct options", () => {
    const program = new Command();
    registerPublish(program);
    const cmd = program.commands.find((c) => c.name() === "publish");
    expect(cmd).toBeDefined();
    const opts = cmd!.options.map((o) => o.flags);
    expect(opts).toContain("--namespace <ns>");
    expect(opts).toContain("--slug <slug>");
    expect(opts).toContain("-v, --ver <ver>");
    expect(opts).not.toContain("--version <ver>");
  });

  it("registers me command with skills and stars subcommands", () => {
    const program = new Command();
    registerMe(program);
    const cmd = program.commands.find((c) => c.name() === "me");
    expect(cmd).toBeDefined();
    const subNames = cmd!.commands.map((c) => c.name());
    expect(subNames).toContain("skills");
    expect(subNames).toContain("stars");
  });

  it("registers versions command", () => {
    const program = new Command();
    registerVersions(program);
    expect(getCommandNames(program)).toContain("versions");
  });

  it("registers notifications command with subcommands", () => {
    const program = new Command();
    registerNotifications(program);
    const cmd = program.commands.find((c) => c.name() === "notifications");
    expect(cmd).toBeDefined();
    const subNames = cmd!.commands.map((c) => c.name());
    expect(subNames).toContain("list");
    expect(subNames).toContain("read");
    expect(subNames).toContain("read-all");
  });

  it("registers reviews command with subcommands", () => {
    const program = new Command();
    registerReviews(program);
    const cmd = program.commands.find((c) => c.name() === "reviews");
    expect(cmd).toBeDefined();
    const subNames = cmd!.commands.map((c) => c.name());
    expect(subNames).toContain("my");
  });

  it("registers namespaces command", () => {
    const program = new Command();
    registerNamespaces(program);
    expect(getCommandNames(program)).toContain("namespaces");
  });

  it("registers resolve command", () => {
    const program = new Command();
    registerResolve(program);
    expect(getCommandNames(program)).toContain("resolve");
  });

  it("registers rating and rate commands", () => {
    const program = new Command();
    registerRating(program);
    registerRate(program);
    const names = getCommandNames(program);
    expect(names).toContain("rating");
    expect(names).toContain("rate");
  });

  it("registers star command", () => {
    const program = new Command();
    registerStar(program);
    expect(getCommandNames(program)).toContain("star");
  });

  it("registers delete command", () => {
    const program = new Command();
    registerDelete(program);
    expect(getCommandNames(program)).toContain("delete");
  });

  it("registers archive command", () => {
    const program = new Command();
    registerArchive(program);
    expect(getCommandNames(program)).toContain("archive");
  });

  it("registers report command", () => {
    const program = new Command();
    registerReport(program);
    expect(getCommandNames(program)).toContain("report");
  });

  it("registers search command", () => {
    const program = new Command();
    registerSearch(program);
    expect(getCommandNames(program)).toContain("search");
  });

  it("registers install command", () => {
    const program = new Command();
    registerInstall(program);
    expect(getCommandNames(program)).toContain("install");
  });

  it("registers download command", () => {
    const program = new Command();
    registerDownload(program);
    expect(getCommandNames(program)).toContain("download");
  });

  it("registers add command", () => {
    const program = new Command();
    registerAdd(program);
    expect(getCommandNames(program)).toContain("add");
  });

  it("registers init command", () => {
    const program = new Command();
    registerInit(program);
    expect(getCommandNames(program)).toContain("init");
  });

  it("registers list command", () => {
    const program = new Command();
    registerList(program);
    expect(getCommandNames(program)).toContain("list");
  });

  it("registers remove command", () => {
    const program = new Command();
    registerRemove(program);
    expect(getCommandNames(program)).toContain("remove");
  });

  it("registers uninstall command", () => {
    const program = new Command();
    registerUninstall(program);
    expect(getCommandNames(program)).toContain("uninstall");
  });

  it("registers logout command", () => {
    const program = new Command();
    registerLogout(program);
    expect(getCommandNames(program)).toContain("logout");
  });
});
