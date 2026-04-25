import { ApiClient } from "./api-client.js";
import { parseSkillName } from "./skill-name.js";
import { searchSkills, runInteractiveSearch } from "./interactive-search.js";

export interface ResolvedSkill {
  namespace: string;
  slug: string;
  userSpecified: boolean;
}

/**
 * 解析 skill 的 namespace，支持智能搜索
 *
 * @param client - API 客户端
 * @param slug - 输入的 skill 名称（可能包含 namespace）
 * @param explicitNamespace - 通过 --namespace 选项显式指定的 namespace
 * @returns 解析后的 namespace 和 slug
 */
export async function resolveSkillNamespace(
  client: ApiClient,
  slug: string,
  explicitNamespace?: string
): Promise<ResolvedSkill> {
  const { namespace: parsedNs, slug: actualSlug } = parseSkillName(slug);

  if (explicitNamespace) {
    return { namespace: explicitNamespace, slug: actualSlug, userSpecified: true };
  }

  if (parsedNs !== "global") {
    return { namespace: parsedNs, slug: actualSlug, userSpecified: true };
  }

  const results = await searchSkills(client, actualSlug, 50);

  const seen = new Set<string>();
  const uniqueResults = results.filter((r) => {
    const key = `${r.namespace}/${r.name}`;
    if (!seen.has(key)) {
      seen.add(key);
      return true;
    }
    return false;
  });

  if (uniqueResults.length === 0) {
    throw new Error(`Skill not found: ${actualSlug}`);
  }

  if (uniqueResults.length === 1) {
    return {
      namespace: uniqueResults[0].namespace,
      slug: uniqueResults[0].name,
      userSpecified: false,
    };
  }

  const selected = await runInteractiveSearch(client, actualSlug);
  if (!selected) {
    throw new Error("Cancelled");
  }

  const [ns, name] = selected.split("/", 2);
  return { namespace: ns, slug: name, userSpecified: false };
}

/**
 * 简单解析 skill namespace，不触发智能搜索
 * 用于不需要搜索的命令（delete, star 等）
 */
export function parseSkillNamespace(
  slug: string,
  explicitNamespace?: string
): ResolvedSkill {
  const { namespace: parsedNs, slug: actualSlug } = parseSkillName(slug);

  if (explicitNamespace) {
    return { namespace: explicitNamespace, slug: actualSlug, userSpecified: true };
  }

  return {
    namespace: parsedNs,
    slug: actualSlug,
    userSpecified: parsedNs !== "global",
  };
}
