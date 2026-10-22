// Development-only source inventory. Never imported by the application bundle.
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const root = fileURLToPath(new URL('../../', import.meta.url))
const destination = path.join(root, 'docs/frontend-analytics-manifest.json')
const sourceRoot = path.join(root, 'web/src')
const markerProp = /^(data-analytics-id|analyticsId|[a-z]+AnalyticsId)$/
const idPattern = /^[a-z]+(?:\.[a-z]+){2,}$/
const passthrough = new Set(['analyticsId', 'confirmAnalyticsId', 'cancelAnalyticsId', 'closeAnalyticsId', 'copyAnalyticsId', 'downloadAnalyticsId', 'skillhubAnalyticsId', 'clawhubAnalyticsId', 'folderAnalyticsId', 'pickerAnalyticsId'])
const navigationFiles = new Set(['web/src/app/layout.tsx', 'web/src/pages/dashboard.tsx', 'web/src/features/namespace/namespace-management-nav.tsx'])

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name, 'en')).flatMap(entry => entry.isDirectory() ? walk(path.join(dir, entry.name)) : [path.join(dir, entry.name)])
}
function parse(file) {
  return ts.createSourceFile(file, fs.readFileSync(path.join(root, file), 'utf8'), ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX)
}
function visit(node, fn) {
  fn(node)
  ts.forEachChild(node, child => visit(child, fn))
}
function line(sf, node) {
  return sf.getLineAndCharacterOfPosition(node.getStart(sf)).line + 1
}
function properties(node, sf) {
  return Object.fromEntries(node.properties.filter(ts.isPropertyAssignment).map(prop => [prop.name.getText(sf), prop.initializer]))
}
function values(node, sf, file) {
  if (!node) throw new Error(`Missing marker value in ${file}`)
  if (ts.isStringLiteral(node)) {
    if (!idPattern.test(node.text)) throw new Error(`Nonsemantic marker in ${file}:${line(sf, node)}`)
    return { ids: [node.text] }
  }
  if (ts.isJsxExpression(node) || ts.isParenthesizedExpression(node)) return values(node.expression, sf, file)
  if (ts.isConditionalExpression(node)) {
    const yes = values(node.whenTrue, sf, file)
    const no = values(node.whenFalse, sf, file)
    return { ids: [...new Set([...(yes.ids ?? []), ...(no.ids ?? [])])].sort(), conditional: true, ...(yes.forward || no.forward ? { forward: yes.forward ?? no.forward } : {}) }
  }
  if (ts.isIdentifier(node) && node.text === 'undefined') return { ids: [] }
  if (ts.isIdentifier(node) && passthrough.has(node.text)) return { forward: node.text }
  if (ts.isPropertyAccessExpression(node) && node.name.text === 'analyticsId' && navigationFiles.has(file) && ['item', 'card', 'tab'].includes(node.expression.getText(sf))) return { forward: node.getText(sf) }
  throw new Error(`Dynamic analytics value rejected in ${file}:${line(sf, node)}: ${node.getText(sf)}`)
}
function eventFor(tag, prop, attrs, ids) {
  if (ids.includes('skill.detail.render')) return 'structural'
  if (tag === 'SelectItem') return 'radix-item-intent'
  if (tag === 'SelectTrigger') return 'click+keydown-intent'
  if (tag === 'form' || tag === 'SearchBar') return 'submit'
  if (tag === 'UploadZone') return prop === 'pickerAnalyticsId' ? 'click' : prop === 'folderAnalyticsId' ? 'change' : 'click+keydown+change+drop'
  if (tag === 'SelectFilter') return 'change'
  if (tag === 'FileTree' || tag === 'FileTreeNodeComponent') return 'click'
  if (tag === 'SkillCard' || tag === 'ResourceCard' || (attrs.has('onKeyDown') && attrs.has('onClick') && tag !== 'TabsTrigger')) return 'click+keydown-enter-space'
  if (tag === 'input' || tag === 'Input') return attrs.has('onKeyDown') ? 'keydown-enter' : 'change'
  if (tag === 'select') return 'change'
  if (tag === 'option') return 'change-selected-option'
  if (ids.includes('skill.avatar.crop.adjust')) return 'pointerup+keydown-intent'
  if (tag === 'div' && attrs.has('data-analytics-event')) return ids.includes('skill.avatar.file.drop') ? 'drop' : 'click+keydown+drop'
  return 'click'
}

export function buildManifest() {
  const sites = []
  const definitions = []
  for (const full of walk(sourceRoot).filter(file => file.endsWith('.tsx') && !/\.(test|spec)\.tsx$/.test(file))) {
    const file = path.relative(root, full).split(path.sep).join('/')
    const sf = parse(file)
    visit(sf, node => {
      if (ts.isJsxOpeningElement(node) || ts.isJsxSelfClosingElement(node)) {
        const attrs = node.attributes.properties.filter(ts.isJsxAttribute)
        const names = new Set(attrs.map(attr => attr.name.getText(sf)))
        for (const attr of attrs.filter(attr => markerProp.test(attr.name.getText(sf)))) {
          const result = values(attr.initializer, sf, file)
          const tag = node.tagName.getText(sf)
          sites.push({ file, line: line(sf, node), host: tag, prop: attr.name.getText(sf), event: eventFor(tag, attr.name.getText(sf), names, result.ids ?? []), ...result })
        }
      }
      if (ts.isPropertyAssignment(node) && node.name.getText(sf) === 'analyticsId') {
        if (!navigationFiles.has(file)) throw new Error(`Unreviewed navigation metadata in ${file}`)
        definitions.push({ file, line: line(sf, node), ...values(node.initializer, sf, file) })
      }
    })
  }
  const routeFile = 'web/src/app/router.tsx'
  const sf = parse(routeFile)
  const bindings = new Map()
  const routes = []
  let registered = []
  visit(sf, node => {
    if (!ts.isVariableDeclaration(node) || node.parent?.parent?.parent !== sf || !node.initializer || !ts.isCallExpression(node.initializer)) return
    const call = node.initializer
    const callee = call.expression.getText(sf)
    if (['createLazyRouteComponent', 'createRoleProtectedRouteComponent'].includes(callee)) {
      let modulePath
      visit(call.arguments[0], child => {
        if (ts.isCallExpression(child) && child.expression.kind === ts.SyntaxKind.ImportKeyword && ts.isStringLiteral(child.arguments[0])) modulePath = child.arguments[0].text
      })
      const exported = call.arguments[1]
      if (!modulePath?.startsWith('@/') || !exported || !ts.isStringLiteral(exported)) throw new Error('Unresolved lazy route')
      const file = 'web/src/' + modulePath.slice(2) + '.tsx'
      const page = parse(file)
      let implementation
      visit(page, child => {
        if ((ts.isFunctionDeclaration(child) || ts.isVariableDeclaration(child)) && child.name?.getText(page) === exported.text) implementation = { file, line: line(page, child), export: exported.text }
      })
      if (!implementation) throw new Error(`Missing route implementation: ${file} ${exported.text}`)
      bindings.set(node.name.getText(sf), { implementation, roleProtected: callee === 'createRoleProtectedRouteComponent' })
    }
    if (callee === 'createRoute') {
      const props = properties(call.arguments[0], sf)
      if (!ts.isStringLiteral(props.path)) throw new Error('Nonliteral route path')
      const component = props.component?.getText(sf)
      const binding = bindings.get(component)
      routes.push({ name: node.name.getText(sf), path: props.path.text, declaration: { file: routeFile, line: line(sf, node) }, implementation: binding?.implementation ?? null, guarded: !!props.beforeLoad, roleProtected: binding?.roleProtected ?? false })
    }
    if (callee === 'rootRoute.addChildren') registered = call.arguments[0].elements.map(element => element.getText(sf))
  })
  if (routes.length !== registered.length || routes.some(route => !registered.includes(route.name)) || new Set(registered).size !== registered.length) throw new Error('Route declaration/registration drift')
  const ids = new Set([...sites, ...definitions].flatMap(site => site.ids ?? []))
  return {
    schema: 1,
    description: 'Source declarations, not runtime DOM counts, pageviews, successful outcomes or a closed runtime enum. Forwarding sites require explicit callers. See frontend-analytics.md for gaps and event policy.',
    counts: { routes: routes.length, uniqueIds: ids.size, literalJsxSites: sites.filter(site => site.ids?.length).length, literalJsxValues: sites.reduce((count, site) => count + (site.ids?.length ?? 0), 0), navigationDefinitions: definitions.length, forwardingSites: sites.filter(site => site.forward).length },
    routes,
    definitions,
    sites,
  }
}
function serialize(manifest) {
  // Compact one-record-per-line projection; source and event stay reviewable together.
  return '{\n  "schema": 1,\n  "description": ' + JSON.stringify(manifest.description) + ',\n  "counts": ' + JSON.stringify(manifest.counts) + ',\n' + ['routes', 'definitions', 'sites'].map(key => '  "' + key + '": [\n' + manifest[key].map(row => '    ' + JSON.stringify(row)).join(',\n') + '\n  ]').join(',\n') + '\n}\n'
}
if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const manifest = buildManifest()
  const text = serialize(manifest)
  if (process.argv.includes('--write')) fs.writeFileSync(destination, text)
  else if (fs.readFileSync(destination, 'utf8') !== text) throw new Error('Analytics manifest drift. Review source, then run node web/scripts/analytics-manifest.mjs --write from the repository root.')
  console.log(JSON.stringify(manifest.counts))
}
