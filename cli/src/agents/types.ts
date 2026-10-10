export interface AgentProfile {
  id: string
  displayName: string
  projectRoots(cwd: string): string[]
  userRoots(home: string): string[]
  detectInstalled(cwd: string, home: string): Promise<AgentCandidate[]>
}

export interface AgentCandidate {
  agent: string
  rootDir: string
  scope: 'project' | 'user'
  source: 'detected' | 'fallback' | 'explicit'
}
