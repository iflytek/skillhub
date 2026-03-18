import type { Code, Root } from 'mdast'
import { visit } from 'unist-util-visit'

const BASH_PREFIX_PATTERN = /^(?:\$ |pip3? |python3? -m |python3? |npm |pnpm |yarn |npx |git |make |curl |wget |docker(?:-compose)? |kubectl |helm |cd |ls |cat |cp |mv |rm |mkdir |chmod |export |set |echo )/m
const PYTHON_PATTERN = /(?:^|\n)(?:from [\w.]+ import |import [\w.]+|def \w+\(|class \w+|with open\(|print\(|if __name__ == ['"]__main__['"]|for \w+ in |try:|except )/
const SQL_PATTERN = /^(?:select|insert\s+into|update|delete\s+from|create\s+table|alter\s+table|with\s+\w+\s+as)\b/im
const TYPESCRIPT_PATTERN = /(?:^|\n)(?:interface \w+|type \w+\s*=|import type |export type |export interface |const \w+:\s|:\s(?:string|number|boolean|Record<|Array<)|as const\b)/
const JAVASCRIPT_PATTERN = /(?:^|\n)(?:const |let |var |function \w+\(|export default |export function |module\.exports|import .* from |=>)/
const YAML_LINE_PATTERN = /^(\s*-\s+)?[\w"'./-]+:\s*.+$/m

function looksLikeJson(value: string) {
  try {
    JSON.parse(value)
    return true
  } catch {
    return false
  }
}

export function inferMarkdownCodeLanguage(value: string): string | undefined {
  const trimmed = value.trim()

  if (!trimmed) {
    return undefined
  }

  if (looksLikeJson(trimmed)) {
    return 'json'
  }

  if (PYTHON_PATTERN.test(trimmed)) {
    return 'python'
  }

  if (BASH_PREFIX_PATTERN.test(trimmed)) {
    return 'bash'
  }

  if (SQL_PATTERN.test(trimmed)) {
    return 'sql'
  }

  if (TYPESCRIPT_PATTERN.test(trimmed)) {
    return 'ts'
  }

  if (JAVASCRIPT_PATTERN.test(trimmed)) {
    return 'javascript'
  }

  if (trimmed.includes(':') && YAML_LINE_PATTERN.test(trimmed)) {
    return 'yaml'
  }

  return undefined
}

export function remarkInferCodeLanguage() {
  return (tree: Root) => {
    visit(tree, 'code', (node: Code) => {
      if (!node.lang) {
        node.lang = inferMarkdownCodeLanguage(node.value)
      }
    })
  }
}
