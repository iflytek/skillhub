import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

export interface AgentInfo {
  key: string;
  name: string;
  projectPath: string;
  globalPath: string;
}

const AGENTS: AgentInfo[] = [
  { key: "claude-code", name: "Claude Code", projectPath: ".claude/skills", globalPath: ".claude/skills" },
  { key: "cursor", name: "Cursor", projectPath: ".agents/skills", globalPath: ".cursor/skills" },
  { key: "codex", name: "Codex", projectPath: ".agents/skills", globalPath: ".codex/skills" },
  { key: "opencode", name: "OpenCode", projectPath: ".agents/skills", globalPath: ".config/opencode/skills" },
  { key: "github-copilot", name: "GitHub Copilot", projectPath: ".agents/skills", globalPath: ".copilot/skills" },
  { key: "cline", name: "Cline", projectPath: ".agents/skills", globalPath: ".agents/skills" },
  { key: "windsurf", name: "Windsurf", projectPath: ".windsurf/skills", globalPath: ".codeium/windsurf/skills" },
  { key: "gemini-cli", name: "Gemini CLI", projectPath: ".agents/skills", globalPath: ".gemini/skills" },
  { key: "roo", name: "Roo Code", projectPath: ".roo/skills", globalPath: ".roo/skills" },
  { key: "continue", name: "Continue", projectPath: ".continue/skills", globalPath: ".continue/skills" },
  { key: "openhands", name: "OpenHands", projectPath: ".openhands/skills", globalPath: ".openhands/skills" },
  { key: "qoder", name: "Qoder", projectPath: ".qoder/skills", globalPath: ".qoder/skills" },
  { key: "trae", name: "Trae", projectPath: ".trae/skills", globalPath: ".trae/skills" },
  { key: "kiro-cli", name: "Kiro CLI", projectPath: ".kiro/skills", globalPath: ".kiro/skills" },
  { key: "qwen-code", name: "Qwen Code", projectPath: ".qwen/skills", globalPath: ".qwen/skills" },
];

export function getAllAgents(): AgentInfo[] {
  return AGENTS;
}

export function detectInstalledAgents(): AgentInfo[] {
  const home = homedir();
  return AGENTS.filter((agent) => {
    const globalPath = join(home, agent.globalPath);
    return existsSync(globalPath);
  });
}

export function getAgentByKey(key: string): AgentInfo | undefined {
  return AGENTS.find((a) => a.key === key);
}
