import skillContent from '@/docs/skill.md?raw'

export function RegistrySkillPage() {
  return (
    <pre className="whitespace-pre-wrap break-words p-0 m-0 font-mono text-sm">
      {skillContent}
    </pre>
  )
}
