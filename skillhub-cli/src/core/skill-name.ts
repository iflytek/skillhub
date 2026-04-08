export interface ParsedSkillName {
  namespace: string;
  slug: string;
}

export function parseSkillName(input: string, defaultNamespace = "global"): ParsedSkillName {
  const parts = input.split("/");
  if (parts.length >= 2) {
    return { namespace: parts[0], slug: parts.slice(1).join("/") };
  }
  return { namespace: defaultNamespace, slug: input };
}
