/**
 * Telemetry utility for anonymous usage tracking.
 * 
 * Respects the following environment variables:
 * - DISABLE_TELEMETRY=1 (or 'true')
 * - DO_NOT_TRACK=1
 * 
 * Telemetry is automatically disabled in CI environments.
 */

export interface TelemetryEvent {
  command: string;
  args?: string[];
  options?: Record<string, unknown>;
}

export interface TelemetryConfig {
  enabled: boolean;
  reason?: string;
}

/**
 * Check if telemetry should be disabled.
 * Respects user preferences and CI environments.
 */
export function isTelemetryDisabled(): TelemetryConfig {
  // Check CI environment
  if (process.env.CI === 'true' || process.env.CONTINUOUS_INTEGRATION === 'true') {
    return { enabled: false, reason: 'CI environment' };
  }

  // Check explicit opt-out flags
  const disableTelemetry = process.env.DISABLE_TELEMETRY;
  const doNotTrack = process.env.DO_NOT_TRACK;

  if (disableTelemetry === '1' || disableTelemetry === 'true') {
    return { enabled: false, reason: 'DISABLE_TELEMETRY is set' };
  }

  if (doNotTrack === '1' || doNotTrack === 'true') {
    return { enabled: false, reason: 'DO_NOT_TRACK is set' };
  }

  // Check Node.js built-in doNotTrack
  if (process.env.NODE_OPTIONS?.includes('do-not-track')) {
    return { enabled: false, reason: 'NODE_OPTIONS includes do-not-track' };
  }

  return { enabled: true };
}

/**
 * Track a command execution event.
 * Currently a stub - actual tracking implementation would send to a telemetry endpoint.
 */
export function trackEvent(event: TelemetryEvent): void {
  const { enabled, reason } = isTelemetryDisabled();
  
  if (!enabled) {
    // Silently skip tracking
    return;
  }

  // TODO: Implement actual telemetry tracking
  // For now, this is a stub that could be expanded to:
  // - Send events to a configured endpoint
  // - Batch events and send periodically
  // - Store events locally if offline
  //
  // Example implementation:
  // if (process.env.SKILLHUB_TELEMETRY_URL) {
  //   fetch(process.env.SKILLHUB_TELEMETRY_URL, {
  //     method: 'POST',
  //     headers: { 'Content-Type': 'application/json' },
  //     body: JSON.stringify({ event, timestamp: Date.now() })
  //   });
  // }
}

/**
 * Track a command execution.
 * Call this at the start of each command.
 */
export function trackCommand(command: string, args?: string[], options?: Record<string, unknown>): void {
  trackEvent({ command, args, options });
}

/**
 * Get telemetry status for display (e.g., in --help or version output).
 */
export function getTelemetryStatus(): string {
  const { enabled, reason } = isTelemetryDisabled();
  
  if (!enabled) {
    return `Telemetry: Disabled (${reason})`;
  }
  
  return 'Telemetry: Enabled (anonymous usage collection)';
}
