import { readFileSync, writeFileSync, existsSync, chmodSync } from "node:fs";
import { homedir } from "node:os";
import { join, dirname } from "node:path";
import { mkdir } from "node:fs/promises";

const TOKEN_DIR = join(homedir(), ".skillhub");
const TOKEN_FILE = join(TOKEN_DIR, "token");

export async function readToken(): Promise<string | null> {
  if (!existsSync(TOKEN_FILE)) return null;
  return readFileSync(TOKEN_FILE, "utf-8").trim();
}

export async function writeToken(token: string): Promise<void> {
  if (!existsSync(TOKEN_DIR)) {
    await mkdir(TOKEN_DIR, { recursive: true });
  }
  writeFileSync(TOKEN_FILE, token);
  try {
    chmodSync(TOKEN_FILE, 0o600);
  } catch {
    // Permission change not critical
  }
}

export async function removeToken(): Promise<void> {
  if (existsSync(TOKEN_FILE)) {
    const { unlinkSync } = await import("node:fs");
    unlinkSync(TOKEN_FILE);
  }
}

export async function requireToken(): Promise<string> {
  const token = await readToken();
  if (!token) {
    throw new Error("Not authenticated. Run `skillhub login` first.");
  }
  return token;
}
