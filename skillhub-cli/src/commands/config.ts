import { Command } from "commander";
import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import { success, error, info, dim } from "../utils/logger.js";

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
    .description("List all configuration sources and their values")
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
    .description("Set registry URL in ~/.skillhub/config.json")
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
    .description("Get registry configuration value")
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
      info(`${yellow("Environment variable setup for SKILLHUB_REGISTRY:\n")}`);

      info(`${cyan("🔹 Temporary (current session only):")}\n`);

      info(`  ${green("Linux/macOS:")}`);
      info(`    ${cyan(`export SKILLHUB_REGISTRY="http://<skillhub-ip>:<backend-port>"`)}`);
      info(`    ${dim("# Example: export SKILLHUB_REGISTRY=\"http://192.168.1.100:8080\"")}\n`);

      info(`  ${green("Windows CMD:")}`);
      info(`    ${cyan(`set SKILLHUB_REGISTRY=http://<skillhub-ip>:<backend-port>`)}`);
      info(`    ${dim("# Example: set SKILLHUB_REGISTRY=http://192.168.1.100:8080")}\n`);

      info(`  ${green("Windows PowerShell:")}`);
      info(`    ${cyan(`$env:SKILLHUB_REGISTRY="http://<skillhub-ip>:<backend-port>"`)}`);
      info(`    ${dim("# Example: $env:SKILLHUB_REGISTRY='http://192.168.1.100:8080'")}\n`);

      info(`${cyan("🔹 Permanent (survives terminal restart):")}\n`);

      info(`  ${green("Linux/macOS (~/.bashrc or ~/.zshrc):")}`);
      info(`    ${cyan(`echo 'export SKILLHUB_REGISTRY="http://<ip>:<port>"' >> ~/.bashrc`)}`);
      info(`    ${cyan(`source ~/.bashrc`)}`);
      info(`    ${dim("# Add to ~/.bashrc for bash, ~/.zshrc for zsh")}\n`);

      info(`  ${green("Windows (User environment variable):")}`);
      info(`    ${cyan(`setx SKILLHUB_REGISTRY "http://<skillhub-ip>:<backend-port>"`)}`);
      info(`    ${dim("# Restart terminal after running this command")}\n`);

      info(`  ${green("PowerShell (User profile):")}`);
      info(`    ${cyan(`[System.Environment]::SetEnvironmentVariable('SKILLHUB_REGISTRY', 'http://<ip>:<port>', 'User')`)}`);
      info(`    ${dim("# Restart PowerShell after running this command")}\n`);

      info(`${cyan("📋 Configuration priority (highest to lowest):")}`);
      info(`    1. ${green("--registry flag")} (one-time, per command)`);
      info(`    2. ${green("SKILLHUB_REGISTRY")} (environment variable)`);
      info(`    3. ${green("~/.skillhub/config.json")} (config file)`);
      info(`    4. ${dim("http://localhost:8080")} (default)\n`);

      info(`${cyan("💡 Quick examples:")}`);
      info(`    skillhub config set registry http://192.168.1.100:8080`);
      info(`    skillhub --registry http://192.168.1.100:8080 explore`);
      info(`    skillhub config list\n`);
    });
}
