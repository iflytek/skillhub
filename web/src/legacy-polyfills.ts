/**
 * Polyfills for older browsers (e.g. Chromium 83 on Debian 10) that lack a few
 * ES2021+ runtime methods used by bundled dependencies. esbuild only transpiles
 * syntax, not runtime APIs, so we patch the prototypes here before any other
 * module runs.
 *
 * TypeScript's lib target is ES2020, so the methods we patch are referenced via
 * string keys / loose casts to avoid compile-time type errors.
 */

type AnyStringReplacer = (substring: string, ...args: unknown[]) => string

const StringProto = String.prototype as unknown as Record<string, unknown>
const ArrayProto = Array.prototype as unknown as Record<string, unknown>
const ObjectCtor = Object as unknown as Record<string, unknown>

if (typeof StringProto.replaceAll !== 'function') {
  Object.defineProperty(String.prototype, 'replaceAll', {
    configurable: true,
    writable: true,
    value: function replaceAll(
      this: string,
      search: string | RegExp,
      replacement: string | AnyStringReplacer,
    ): string {
      if (search instanceof RegExp) {
        if (!search.flags.includes('g')) {
          throw new TypeError('String.prototype.replaceAll called with a non-global RegExp argument')
        }
        return this.replace(search, replacement as string)
      }
      const needle = String(search)
      if (needle === '') {
        return this.replace(new RegExp('', 'g'), replacement as string)
      }
      const escaped = needle.replace(/[-\\^$*+?.()|[\]{}]/g, '\\$&')
      return this.replace(new RegExp(escaped, 'g'), replacement as string)
    },
  })
}

if (typeof ArrayProto.at !== 'function') {
  Object.defineProperty(Array.prototype, 'at', {
    configurable: true,
    writable: true,
    value: function at(this: unknown[], index: number) {
      const len = this.length
      const i = Math.trunc(index) || 0
      const resolved = i < 0 ? len + i : i
      if (resolved < 0 || resolved >= len) return undefined
      return this[resolved]
    },
  })
}

if (typeof StringProto.at !== 'function') {
  Object.defineProperty(String.prototype, 'at', {
    configurable: true,
    writable: true,
    value: function at(this: string, index: number) {
      const len = this.length
      const i = Math.trunc(index) || 0
      const resolved = i < 0 ? len + i : i
      if (resolved < 0 || resolved >= len) return undefined
      return this.charAt(resolved)
    },
  })
}

if (typeof ObjectCtor.hasOwn !== 'function') {
  Object.defineProperty(Object, 'hasOwn', {
    configurable: true,
    writable: true,
    value: function hasOwn(target: object, property: PropertyKey) {
      return Object.prototype.hasOwnProperty.call(target, property)
    },
  })
}
