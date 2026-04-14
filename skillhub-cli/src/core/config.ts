import { existsSync, readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

const CONFIG_DIR = join(homedir(), ".skillhub");
const CONFIG_FILE = join(CONFIG_DIR, "config.json");

export interface CliConfig {
  registry: string;
  dir?: string;
}

const DEFAULT_CONFIG: CliConfig = {
  registry: "http://localhost:8080",
};

export function loadConfig(): CliConfig {
  if (!existsSync(CONFIG_FILE)) return { ...DEFAULT_CONFIG };
  try {
    const raw = readFileSync(CONFIG_FILE, "utf-8");
    return { ...DEFAULT_CONFIG, ...JSON.parse(raw) };
  } catch {
    return { ...DEFAULT_CONFIG };
  }
}

export function saveConfig(config: Partial<CliConfig>): void {
  const existing = loadConfig();
  const merged = { ...existing, ...config };
  if (!existsSync(CONFIG_DIR)) {
    mkdirSync(CONFIG_DIR, { recursive: true });
  }
  writeFileSync(CONFIG_FILE, JSON.stringify(merged, null, 2));
}
