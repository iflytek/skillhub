const builtInLabels: Record<string, string> = {
  official: '官方', 'ai-competition': '人工智能竞赛', 'ai competition': '人工智能竞赛', aicompetition: '人工智能竞赛',
  'aicomp-2026': '2026 人工智能竞赛', coding: '编程', automation: '自动化',
  productivity: '效率工具', entertainment: '娱乐', image: '图像',
  communication: '沟通协作', health: '健康',
};

/** Translate only known built-ins; the original slug remains the API filter. */
export function catalogLabel(item: { slug: string; displayName?: string }) {
  const name = item.displayName?.trim() || undefined;
  if (name && /\p{Script=Han}/u.test(name)) return name;
  return builtInLabels[item.slug.toLowerCase()] ?? builtInLabels[name?.toLowerCase() ?? ''] ?? name ?? item.slug;
}
