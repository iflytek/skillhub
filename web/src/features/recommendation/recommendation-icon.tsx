import { BookOpen, CalendarDays, ClipboardList, FileText, GraduationCap, Layout, ListChecks, Megaphone, Network, Package, Scale, ShieldCheck, Timer } from 'lucide-react'

const icons = {
  'meeting-note-summarizer': FileText, 'frontend-design': Layout, 'documentation-writer': BookOpen,
  'diagram-maker': Network, 'decision-matrix': Scale, 'ai-claim-checker': ShieldCheck,
  'daily-standup-journal': CalendarDays, 'time-blocking-scheduler': Timer, 'storytelling-advisor': Megaphone,
  'exam-ready': GraduationCap, 'study-strategy-selector': BookOpen, 'retrieval-practice-generator': ListChecks,
  'cue-omni-reader': BookOpen, 'agentguard': ShieldCheck, 'plugin-scanner': Package,
  'linkedin-post-formatter': FileText, 'zero-slop': FileText,
}
export function RecommendationSkillIcon({ slug, className = 'h-5 w-5' }: { slug: string; className?: string }) {
  const Icon = Object.hasOwn(icons, slug) ? icons[slug as keyof typeof icons] : Package
  return <Icon aria-hidden className={`${className} shrink-0 text-muted-foreground`} strokeWidth={1.75} />
}
export function RecommendationTopicIcon({ id, className = 'h-6 w-6' }: { id: string; className?: string }) {
  const Icon = ({ 'daily-work': ClipboardList, 'design-docs': Layout, learning: GraduationCap, writing: Megaphone, research: BookOpen, 'safe-tools': ShieldCheck })[id] ?? Package
  return <Icon aria-hidden className={`${className} shrink-0 text-muted-foreground`} strokeWidth={1.75} />
}
