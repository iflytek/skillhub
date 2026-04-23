import { Command } from "commander";
import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { success, error, info } from "../utils/logger.js";
import chalk from "chalk";

const CONFIG_DIR = join(homedir(), ".skillhub");
const CONFIG_FILE = join(CONFIG_DIR, "config.json");

const cyan = (s: string) => `\x1b[36m${s}\x1b[0m`;
const yellow = (s: string) => `\x1b[33m${s}\x1b[0m`;
const green = (s: string) => `\x1b[32m${s}\x1b[0m`;

export function registerConfig(program: Command) {
  const configCmd = program
    .command("config")
    .description("Manage SkillHub CLI configuration")
    .addHelpCommand(false);

  configCmd
    .command("list")
    .description("Show all config values and their sources")
    .action(() => {
      const env = process.env.SKILLHUB_REGISTRY;
      let fileConfig: { registry?: string } = {};
      if (existsSync(CONFIG_FILE)) {
        try {
          fileConfig = JSON.parse(readFileSync(CONFIG_FILE, "utf-8"));
        } catch {
          // Invalid config file, ignore
        }
      }

      info("Current configuration:\n");

      info(`  ${cyan("Environment")}`);
      info(`    SKILLHUB_REGISTRY: ${env || dim("not set")}`);

      info(`\n  ${cyan("Config file")}`);
      info(`    ~/.skillhub/config.json: ${fileConfig.registry || dim("not set")}`);

      info(`\n  ${cyan("Default")}`);
      info(`    http://localhost:8080\n`);

      const active = env || fileConfig.registry || "http://localhost:8080";
      const source = env
        ? green("environment variable")
        : fileConfig.registry
          ? yellow("config file")
          : dim("default");

      success(`Active registry: ${active}`);
      info(`Source: ${source}`);
    });

  configCmd
    .command("set <value>")
    .description("Set registry URL (e.g., https://api.example.com)")
    .action((value: string) => {
      if (!existsSync(CONFIG_DIR)) {
        mkdirSync(CONFIG_DIR, { recursive: true });
      }

      let config: Record<string, string> = {};
      if (existsSync(CONFIG_FILE)) {
        try {
          config = JSON.parse(readFileSync(CONFIG_FILE, "utf-8"));
        } catch {
          // Invalid config file, start fresh
        }
      }

      config.registry = value;
      writeFileSync(CONFIG_FILE, JSON.stringify(config, null, 2));
      success(`Registry set to: ${value}`);
      info(`Config file: ${CONFIG_FILE}`);
    });

  configCmd
    .command("get")
    .description("Show the current registry URL")
    .option("--source <source>", "Source: env, file, or resolved (default)")
    .action((opts: { source?: string }) => {
      const source = opts.source || "resolved";
      const envValue = process.env.SKILLHUB_REGISTRY;
      const fileValue = existsSync(CONFIG_FILE)
        ? (() => {
            try {
              return JSON.parse(readFileSync(CONFIG_FILE, "utf-8")).registry;
            } catch {
              return null;
            }
          })()
        : null;

      if (source === "env") {
        if (envValue) {
          success(envValue);
          dim("Source: environment variable");
        } else {
          dim("Environment variable SKILLHUB_REGISTRY is not set");
          process.exitCode = 1;
        }
      } else if (source === "file") {
        if (fileValue) {
          success(fileValue);
          dim("Source: config file");
        } else {
          dim("Config file does not have registry set");
          process.exitCode = 1;
        }
      } else if (source === "resolved") {
        const defaultValue = "http://localhost:8080";
        const value = envValue || fileValue || defaultValue;
        const actualSource = envValue
          ? "environment variable"
          : fileValue
            ? "config file"
            : "default";

        success(value);
        dim(`Source: ${actualSource}`);
        if (!envValue) {
          dim(`To override with env var: export SKILLHUB_REGISTRY="${value}"`);
        }
      } else {
        error(`Unknown source: ${source}. Supported sources: env, file, resolved`);
        process.exitCode = 1;
      }
    });

  configCmd
    .command("show-env-instructions")
    .description("Show how to set SKILLHUB_REGISTRY environment variable")
    .action(() => {
      const lines: string[] = [];

      lines.push(yellow("Environment variable setup for SKILLHUB_REGISTRY:"));
      lines.push("");

      lines.push(cyan("🔹 Temporary (current session only):"));
      lines.push("");

      lines.push(`  ${green("Linux/macOS:")}`);
      lines.push(`    ${cyan('export SKILLHUB_REGISTRY="http://<skillhub-ip>:<backend-port>"')}`);
      lines.push(`    ${chalk.dim("# Example: export SKILLHUB_REGISTRY=\"http://192.168.1.100:8080\"")}`);
      lines.push("");

      lines.push(`  ${green("Windows CMD:")}`);
      lines.push(`    ${cyan("set SKILLHUB_REGISTRY=http://<skillhub-ip>:<backend-port>")}`);
      lines.push(`    ${chalk.dim("# Example: set SKILLHUB_REGISTRY=http://192.168.1.100:8080")}`);
      lines.push("");

      lines.push(`  ${green("Windows PowerShell:")}`);
      lines.push(`    ${cyan('$env:SKILLHUB_REGISTRY="http://<skillhub-ip>:<backend-port>"')}`);
      lines.push(`    ${chalk.dim("# Example: $env:SKILLHUB_REGISTRY='http://192.168.1.100:8080'")}`);
      lines.push("");

      lines.push(cyan("🔹 Permanent (survives terminal restart):"));
      lines.push("");

      lines.push(`  ${green("Linux/macOS (~/.bashrc or ~/.zshrc):")}`);
      lines.push(`    ${cyan('echo \'export SKILLHUB_REGISTRY="http://<ip>:<port>"\' >> ~/.bashrc')}`);
      lines.push(`    ${cyan("source ~/.bashrc")}`);
      lines.push(`    ${chalk.dim("# Add to ~/.bashrc for bash, ~/.zshrc for zsh")}`);
      lines.push("");

      lines.push(`  ${green("Windows (User environment variable):")}`);
      lines.push(`    ${cyan('setx SKILLHUB_REGISTRY "http://<skillhub-ip>:<backend-port>"')}`);
      lines.push(`    ${chalk.dim("# Restart terminal after running this command")}`);
      lines.push("");

      lines.push(`  ${green("PowerShell (User profile):")}`);
      lines.push(`    ${cyan("[System.Environment]::SetEnvironmentVariable('SKILLHUB_REGISTRY', 'http://<ip>:<port>', 'User')")}`);
      lines.push(`    ${chalk.dim("# Restart PowerShell after running this command")}`);
      lines.push("");

      lines.push(cyan("📋 Configuration priority (highest to lowest):"));
      lines.push(`    1. ${green("--registry flag")} (one-time, per command)`);
      lines.push(`    2. ${green("SKILLHUB_REGISTRY")} (environment variable)`);
      lines.push(`    3. ${green("~/.skillhub/config.json")} (config file)`);
      lines.push(`    4. ${chalk.dim("http://localhost:8080")} (default)`);
      lines.push("");

      lines.push(cyan("💡 Quick examples:"));
      lines.push(`    skillhub config set http://192.168.1.100:8080`);
      lines.push(`    skillhub --registry http://192.168.1.100:8080 explore`);
      lines.push(`    skillhub config list`);

      console.log(lines.join("\n"));
    });
}
