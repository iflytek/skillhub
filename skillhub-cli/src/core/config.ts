import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";
import type { Command } from "commander";

const CONFIG_DIR = join(homedir(), ".skillhub");
const CONFIG_FILE = join(CONFIG_DIR, "config.json");

export interface CliConfig {
  registry: string;
  dir?: string;
}

const DEFAULT_CONFIG: CliConfig = {
  registry: "http://localhost:8080",
};

export function loadConfig(overrides?: Partial<CliConfig>): CliConfig {
  // Priority: overrides > env > config file > defaults
  const envRegistry = process.env.SKILLHUB_REGISTRY;
  const baseConfig: CliConfig = envRegistry
    ? { registry: envRegistry }
    : { ...DEFAULT_CONFIG };

  if (existsSync(CONFIG_FILE)) {
    try {
      const raw = readFileSync(CONFIG_FILE, "utf-8");
      Object.assign(baseConfig, JSON.parse(raw));
    } catch {
      // Use base config if file is invalid
    }
  }

  // Apply overrides (e.g., from command-line options)
  if (overrides) {
    Object.assign(baseConfig, overrides);
  }

  return baseConfig;
}

/**
 * Helper function to load config with command-line options from Commander.js program
 * Use this in command actions to get config that respects --registry flag
 */
export function loadConfigFromProgram(program: Command): CliConfig {
  const opts = program.opts();
  const overrides: Partial<CliConfig> = {};

  if (opts.registry) {
    overrides.registry = opts.registry as string;
  }

  return loadConfig(overrides);
}

export function saveConfig(config: Partial<CliConfig>): void {
  const existing = loadConfig();
  const merged = { ...existing, ...config };
  if (!existsSync(CONFIG_DIR)) {
    mkdirSync(CONFIG_DIR, { recursive: true });
  }
  writeFileSync(CONFIG_FILE, JSON.stringify(merged, null, 2));
}
