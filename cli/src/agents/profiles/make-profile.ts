import { pathExists } from '../../platform/paths'
import type { AgentProfile, AgentCandidate } from '../types'

async function dirExists(path: string): Promise<boolean> {
  return pathExists(path)
}

export function makeProfile(id: string, displayName: string, projectSkills: string, userSkills: string): AgentProfile {
  return {
    id,
    displayName,
    projectRoots: cwd => [`${cwd}/${projectSkills}`],
    userRoots: home => [`${home}/${userSkills}`],
    async detectInstalled(cwd, home) {
      const roots = [...this.projectRoots(cwd), ...this.userRoots(home)]
      const results: AgentCandidate[] = []
      for (const root of roots) {
        if (await dirExists(root)) {
          results.push({
            agent: this.id,
            rootDir: root,
            scope: root.startsWith(cwd) ? 'project' : 'user',
            source: 'detected'
          })
        }
      }
      return results
    }
  }
}
