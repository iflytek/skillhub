import { mkdirSync, symlinkSync, copyFileSync, readdirSync, lstatSync, unlinkSync, existsSync } from "node:fs";
import { join, dirname, relative } from "node:path";
import { homedir, platform } from "node:os";

export interface SkillInstallResult {
  skillName: string;
  agentKey: string;
  path: string;
  mode: "symlink" | "copy";
  success: boolean;
  error?: string;
}

const UNIVERSAL_PATH = ".agents/skills";

function isUniversalAgent(skillsDir: string): boolean {
  return skillsDir === UNIVERSAL_PATH;
}

function getCanonicalBase(isGlobal: boolean, cwd: string): string {
  const home = homedir();
  return isGlobal ? join(home, UNIVERSAL_PATH) : join(cwd, UNIVERSAL_PATH);
}

function getAgentBaseDir(skillsDir: string, isGlobal: boolean, cwd: string): string {
  const home = homedir();
  if (isGlobal) {
    return join(home, skillsDir);
  }
  return join(cwd, skillsDir);
}

function removePath(path: string): void {
  try {
    const stat = lstatSync(path);
    if (stat.isSymbolicLink()) {
      unlinkSync(path);
    } else if (stat.isDirectory()) {
      for (const entry of readdirSync(path)) {
        removePath(join(path, entry));
      }
      if (platform() !== "win32") {
        try { unlinkSync(path); } catch { }
      }
    } else {
      unlinkSync(path);
    }
  } catch { }
}

function ensureDir(path: string): void {
  if (!existsSync(path)) {
    mkdirSync(path, { recursive: true });
  }
}

function createSymlink(target: string, linkPath: string): boolean {
  try {
    if (target === linkPath) {
      return true;
    }

    removePath(linkPath);

    const linkDir = dirname(linkPath);
    const resolvedLinkDir = linkDir.startsWith("~") ? join(homedir(), linkDir.slice(1)) : linkDir;
    ensureDir(resolvedLinkDir);

    const relativePath = relative(resolvedLinkDir, target);
    const symlinkType = platform() === "win32" ? "junction" : "dir";

    symlinkSync(relativePath, linkPath, symlinkType);
    return true;
  } catch {
    return false;
  }
}

export function installSkill(
  skillDir: string,
  skillName: string,
  agentKey: string,
  targetDir: string,
  mode: "symlink" | "copy",
  isGlobal: boolean,
): SkillInstallResult {
  const cwd = process.cwd();
  const canonicalBase = getCanonicalBase(isGlobal, cwd);
  const canonicalDir = join(canonicalBase, skillName);
  const agentBase = getAgentBaseDir(targetDir, isGlobal, cwd);
  const agentDir = join(agentBase, skillName);

  const agentIsUniversal = isUniversalAgent(targetDir);

  try {
    if (mode === "copy") {
      const copyDestDir = dirname(agentDir);
      const resolvedCopyDestDir = copyDestDir.startsWith("~") ? join(homedir(), copyDestDir.slice(1)) : copyDestDir;
      ensureDir(resolvedCopyDestDir);
      removePath(agentDir);
      mkdirSync(agentDir, { recursive: true });
      copyDir(skillDir, agentDir);
      return { skillName, agentKey, path: agentDir, mode, success: true };
    }

    ensureDir(dirname(canonicalDir));
    removePath(canonicalDir);
    mkdirSync(canonicalDir, { recursive: true });
    copyDir(skillDir, canonicalDir);

    if (isGlobal && agentIsUniversal) {
      return { skillName, agentKey, path: canonicalDir, mode, success: true };
    }

    const symlinkCreated = createSymlink(canonicalDir, agentDir);

    if (!symlinkCreated) {
      const agentLinkDir = dirname(agentDir);
      const resolvedAgentLinkDir = agentLinkDir.startsWith("~") ? join(homedir(), agentLinkDir.slice(1)) : agentLinkDir;
      ensureDir(resolvedAgentLinkDir);
      removePath(agentDir);
      mkdirSync(agentDir, { recursive: true });
      copyDir(skillDir, agentDir);
      return { skillName, agentKey, path: agentDir, mode, success: true };
    }

    return { skillName, agentKey, path: agentDir, mode, success: true };
  } catch (e: any) {
    return { skillName, agentKey, path: agentDir, mode, success: false, error: e.message };
  }
}

function copyDir(src: string, dest: string) {
  mkdirSync(dest, { recursive: true });
  for (const entry of readdirSync(src)) {
    const srcPath = join(src, entry);
    const destPath = join(dest, entry);
    if (lstatSync(srcPath).isDirectory()) {
      copyDir(srcPath, destPath);
    } else {
      copyFileSync(srcPath, destPath);
    }
  }
}
