// Deployment configuration, not an application setting. Review before serving.
import { DEFAULT_RULES, observeSkillhubIntents } from './skillhub-intents.mjs'
import { createUmamiAdapter } from './umami-adapter.mjs'

const config = Object.freeze({
  enabled: false,
  website: '00000000-0000-0000-0000-000000000000', // Replace with your Umami website UUID.
  prefix: '/_skillhub-umami',
  rules: { ...DEFAULT_RULES, 'skill.example.open': 'click' },
})

// Replace this hook with your consent manager's current approval state.
// It must be false until policy/consent permits collection; do not infer approval.
function hasConsent() { return false }

let dispose
function start() {
  if (!config.enabled || !hasConsent() || dispose) return
  const adapter = createUmamiAdapter({ website: config.website, prefix: config.prefix })
  const stopObserver = observeSkillhubIntents({ rules: config.rules, onIntent: adapter.send })
  dispose = () => { stopObserver(); adapter.stop() }
}
function stop() { dispose?.(); dispose = undefined }
window.skillhubAnalytics = Object.freeze({ start, stop })
// Consent manager: call start() on approval, stop() immediately on withdrawal.
start()
