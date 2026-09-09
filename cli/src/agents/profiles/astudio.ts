import { directoryExists } from '../../platform/paths'
import type { AgentProfile } from '../types'

function userSkillsRoot(home: string): string {
  return home.replace(/\\/g, '/').replace(/\/+$/, '') + '/.acode/skills'
}

export const aStudioProfile: AgentProfile = {
  id: 'astudio',
  displayName: 'AStudio',
  projectRoots: () => [],
  userRoots: home => [userSkillsRoot(home)],
  async detectInstalled(_cwd, home) {
    const rootDir = userSkillsRoot(home)
    if (!await directoryExists(rootDir)) return []
    return [{
      agent: this.id,
      rootDir,
      scope: 'user',
      source: 'detected'
    }]
  }
}
