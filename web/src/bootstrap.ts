/**
 * Bootstraps runtime configuration before the React bundle mounts.
 *
 * Deployments inject `/runtime-config.js` at startup, and this file guarantees the app sees either
 * that config or a safe fallback object before importing the main entry.
 */
async function loadRuntimeConfig() {
  await new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = new URL('../runtime-config.js', import.meta.url).toString()
    script.async = false
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Failed to load runtime config'))
    document.head.appendChild(script)
  })
}

function ensureRuntimeConfigFallback() {
  window.__SKILLHUB_RUNTIME_CONFIG__ ??= {
    apiBaseUrl: '',
    appBaseUrl: '',
    authDirectEnabled: 'false',
    authDirectProvider: '',
    authSessionBootstrapEnabled: 'false',
    authSessionBootstrapProvider: '',
    authSessionBootstrapAuto: 'false',
  }
}

void (async () => {
  try {
    await loadRuntimeConfig()
  } catch (error) {
    console.error(error)
    ensureRuntimeConfigFallback()
  }

  await import('./main')
})()
