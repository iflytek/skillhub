import { existsSync, mkdirSync, symlinkSync, copyFileSync, statSync, readdirSync, lstatSync, unlinkSync, rmdirSync } from "node:fs";
import { join } from "node:path";
import { homedir } from "node:os";

export interface SkillInstallResult {
  skillName: string;
  agentKey: string;
  path: string;
  mode: "symlink" | "copy";
  success: boolean;
  error?: string;
}

export function installSkill(
  skillDir: string,
  skillName: string,
  agentKey: string,
  targetDir: string,
  mode: "symlink" | "copy",
  isGlobal: boolean,
): SkillInstallResult {
  const home = homedir();
  const baseDir = isGlobal ? join(home, targetDir) : join(process.cwd(), targetDir);
  const skillTargetDir = join(baseDir, skillName);

  try {
    if (!existsSync(baseDir)) {
      mkdirSync(baseDir, { recursive: true });
    }

    let targetExists = existsSync(skillTargetDir);
    if (!targetExists) {
      try {
        const lst = lstatSync(skillTargetDir);
        targetExists = true;
      } catch {
        targetExists = false;
      }
    }
    if (targetExists) {
      removeDir(skillTargetDir);
    }

    if (mode === "symlink") {
      symlinkSync(skillDir, skillTargetDir, "dir");
    } else {
      copyDir(skillDir, skillTargetDir);
    }

    return { skillName, agentKey, path: skillTargetDir, mode, success: true };
  } catch (e: any) {
    return { skillName, agentKey, path: skillTargetDir, mode, success: false, error: e.message };
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

function removeDir(path: string) {
  const stat = lstatSync(path);
  if (stat.isDirectory() || stat.isSymbolicLink()) {
    if (stat.isSymbolicLink()) {
      // Symlink: just unlink it directly
      unlinkSync(path);
    } else {
      // Real directory: recursive remove
      for (const entry of readdirSync(path)) {
        removeDir(join(path, entry));
      }
      rmdirSync(path);
    }
  } else {
    unlinkSync(path);
  }
}
