async function loadRuntimeConfig() {
  await new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = '/runtime-config.js'
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
