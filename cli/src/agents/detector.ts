import type { AgentProfile } from './types'
import { claudeCodeProfile } from './profiles/claude-code'
import { codexProfile } from './profiles/codex'
import { cursorProfile } from './profiles/cursor'
import { githubCopilotProfile } from './profiles/github-copilot'
import { geminiCliProfile } from './profiles/gemini-cli'
import { openhandsProfile } from './profiles/openhands'
import { windsurfProfile } from './profiles/windsurf'
import { openclawProfile } from './profiles/openclaw'
import { kiroCliProfile } from './profiles/kiro-cli'
import { rooProfile } from './profiles/roo'
import { traeProfile } from './profiles/trae'
import { traeCnProfile } from './profiles/trae-cn'
import { opencodeProfile } from './profiles/opencode'
import { kiloProfile } from './profiles/kilo'

export {
  claudeCodeProfile, codexProfile, cursorProfile, githubCopilotProfile,
  geminiCliProfile, openhandsProfile, windsurfProfile, openclawProfile,
  kiroCliProfile, rooProfile, traeProfile, traeCnProfile,
  opencodeProfile, kiloProfile
}

export const allProfiles: AgentProfile[] = [
  claudeCodeProfile, codexProfile, cursorProfile, githubCopilotProfile,
  geminiCliProfile, openhandsProfile, windsurfProfile, openclawProfile,
  kiroCliProfile, rooProfile, traeProfile, traeCnProfile,
  opencodeProfile, kiloProfile
]

export const profileMap = new Map(allProfiles.map(p => [p.id, p]))
